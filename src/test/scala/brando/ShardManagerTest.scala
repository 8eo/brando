package brando

import akka.actor._
import akka.testkit._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.FunSpecLike

import scala.util.Failure

class ShardManagerTest extends TestKit(ActorSystem("ShardManagerTest")) with FunSpecLike with ImplicitSender {

  import Connection._
  import ShardManager._

  val host = ConfigFactory.load().getString("brando.connection.host")

  describe("creating shards") {
    it("should create a pool of clients mapped to ids") {
      val shards = Seq(
        RedisShard("server1", host, 6379, 0),
        RedisShard("server2", host, 6379, 1),
        RedisShard("server3", host, 6379, 2)
      )

      val shardManager = TestActorRef[ShardManager](ShardManager(shards))

      assert(shardManager.underlyingActor.pool.keys === Set("server1", "server2", "server3"))
    }

    it("should support updating existing shards but not creating new ones") {
      val shards = Seq(
        RedisShard("server1", host, 6379, 0),
        RedisShard("server2", host, 6379, 1),
        RedisShard("server3", host, 6379, 2)
      )

      val shardManager = TestActorRef[ShardManager](ShardManager(shards))

      assert(shardManager.underlyingActor.pool.keys === Set("server1", "server2", "server3"))

      shardManager ! RedisShard("server1", host, 6379, 6)

      assert(shardManager.underlyingActor.pool.keys === Set("server1", "server2", "server3"))

      shardManager ! RedisShard("new_server", host, 6378, 3)

      assert(shardManager.underlyingActor.pool.keys === Set("server1", "server2", "server3"))
    }
  }

  describe("sending requests") {
    ignore("using sentinel") {
      it("should forward each request to the appropriate client transparently") {
        val sentinelProbe = TestProbe()
        val redisProbe    = TestProbe()
        val sentinel      = system.actorOf(Sentinel(listeners = Set(sentinelProbe.ref)))
        val shardManager = system.actorOf(
          ShardManager(
            shards = Seq(SentinelShard("mymaster", 0)),
            sentinelClient = Some(sentinel),
            listeners = Set(redisProbe.ref)
          )
        )

        sentinelProbe.expectMsg(Connecting(host, 26379))
        sentinelProbe.expectMsg(Connected(host, 26379))

        redisProbe.expectMsgClass(classOf[Connecting])
        redisProbe.expectMsgClass(classOf[Connected])

        shardManager ! ("key", Request("SET", "shard_manager_test", "some value"))

        expectMsg(Some(Ok))

        shardManager ! ("key", Request("GET", "shard_manager_test"))

        expectMsg(Some(ByteString("some value")))
      }

      it("should forward each request to the appropriate client transparently") {
        val shards = Seq(
          RedisShard("server1", host, 6379, 0),
          RedisShard("server2", host, 6379, 1),
          RedisShard("server3", host, 6379, 2)
        )

        val sentinelProbe = TestProbe()
        val redisProbe    = TestProbe()
        val sentinel      = system.actorOf(Sentinel(listeners = Set(sentinelProbe.ref)))
        val shardManager = system.actorOf(
          ShardManager(shards = shards, sentinelClient = Some(sentinel), listeners = Set(redisProbe.ref))
        )

        sentinelProbe.expectMsg(Connecting(host, 26379))
        sentinelProbe.expectMsg(Connected(host, 26379))

        redisProbe.expectMsg(Connecting(host, 6379))
        redisProbe.expectMsg(Connecting(host, 6379))
        redisProbe.expectMsg(Connecting(host, 6379))

        redisProbe.expectMsg(Connected(host, 6379))
        redisProbe.expectMsg(Connected(host, 6379))
        redisProbe.expectMsg(Connected(host, 6379))

        shardManager ! ("key", Request("SET", "shard_manager_test", "some value"))

        expectMsg(Some(Ok))

        shardManager ! ("key", Request("GET", "shard_manager_test"))

        expectMsg(Some(ByteString("some value")))
      }
    }

    it("should infer the key from the params list") {
      val shards = Seq(
        RedisShard("server1", host, 6379, 0),
        RedisShard("server2", host, 6379, 1),
        RedisShard("server3", host, 6379, 2)
      )

      val redisProbe   = TestProbe()
      val shardManager = TestActorRef[ShardManager](ShardManager(shards, listeners = Set(redisProbe.ref)))

      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))

      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))

      shardManager ! Request("SET", "shard_manager_test", "some value")

      expectMsg(Some(Ok))

      shardManager ! Request("GET", "shard_manager_test")

      expectMsg(Some(ByteString("some value")))
    }

    it("should fail with IllegalArgumentException when params is empty") {
      val shards = Seq(
        RedisShard("server1", host, 6379, 0),
        RedisShard("server2", host, 6379, 1),
        RedisShard("server3", host, 6379, 2)
      )

      val redisProbe   = TestProbe()
      val shardManager = TestActorRef[ShardManager](ShardManager(shards, listeners = Set(redisProbe.ref)))

      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))

      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))

      shardManager ! Request("SET")

      expectMsgClass(classOf[Failure[IllegalArgumentException]])
    }

    it("should broadcast a Request to all shards") {
      val shards = Seq(
        RedisShard("server1", host, 6379, 0),
        RedisShard("server2", host, 6379, 1),
        RedisShard("server3", host, 6379, 2)
      )

      val redisProbe   = TestProbe()
      val shardManager = TestActorRef[ShardManager](ShardManager(shards, listeners = Set(redisProbe.ref)))

      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))
      redisProbe.expectMsg(Connecting(host, 6379))

      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))
      redisProbe.expectMsg(Connected(host, 6379))

      val listName = scala.util.Random.nextString(5)

      shardManager ! BroadcastRequest("LPUSH", listName, "somevalue")

      shards.foreach(_ => expectMsg(Some(new java.lang.Long(1))))

      shardManager ! BroadcastRequest("LPOP", listName)

      shards.foreach(_ => expectMsg(Some(ByteString("somevalue"))))
    }
  }

  describe("Listening to Shard state changes") {
    it("should notify listeners when a shard connect successfully") {
      val shards = Seq(RedisShard("server1", host, 6379, 0))

      val probe = TestProbe()

      val shardManager = TestActorRef[ShardManager](ShardManager(shards, Set(probe.ref)))

      probe.expectMsg(Connecting(host, 6379))
      probe.expectMsg(Connected(host, 6379))
    }

    it("should notify listeners when a shard fails to connect") {
      val shards = Seq(RedisShard("server2", host, 13579, 1))

      val probe = TestProbe()

      val shardManager = TestActorRef[ShardManager](ShardManager(shards, Set(probe.ref)))

      probe.expectMsg(Connecting(host, 13579))
      probe.expectMsg(ConnectionFailed(host, 13579))
    }

    it("should cleaned up any dead listeners") {

      val shards = Seq(RedisShard("server1", host, 6379, 0))

      val probe1 = TestProbe()
      val probe2 = TestProbe()

      val shardManager = TestActorRef[ShardManager](ShardManager(shards, Set(probe1.ref, probe2.ref))).underlyingActor
      assertResult(2)(shardManager.listeners.size)

      probe1.ref ! PoisonPill

      probe2.expectMsg(Connecting(host, 6379))
      probe2.expectMsg(Connected(host, 6379))

      assertResult(1)(shardManager.listeners.size)

    }

    it("should notify listeners when a shard fails to authenticate") {
      val shards =
        Seq(RedisShard("server1", host, 6379, 0), RedisShard("server2", host, 6379, 1, auth = Some("not-valid-auth")))

      val probe = TestProbe()

      val shardManager = TestActorRef[ShardManager](ShardManager(shards, Set(probe.ref)))

      probe.expectMsg(Connecting(host, 6379))
      probe.expectMsg(Connecting(host, 6379))
      probe.expectMsg(Connected(host, 6379))
      probe.expectMsg(Redis.AuthenticationFailed(host, 6379))
    }
  }
}
