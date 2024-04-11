package brando

import akka.actor._
import akka.testkit._
import com.typesafe.config.ConfigFactory
import org.scalatest._

class RedisClientSentinelTest
    extends TestKit(ActorSystem("RedisClientSentinelTest"))
    with FunSpecLike
    with ImplicitSender {

  import Connection._
  import Sentinel._

  val host = ConfigFactory.load().getString("brando.connection.host")

  ignore("RedisClientSentinel") {
    describe("when connecting") {
      it("should use sentinel to resolve the ip and port") {
        val sentinelProbe = TestProbe()
        val brando        = system.actorOf(RedisSentinel("mymaster", sentinelProbe.ref, 0, None))

        sentinelProbe.expectMsg(Request("SENTINEL", "MASTER", "mymaster"))
      }

      it("should connect to sentinel and redis") {
        val redisProbe    = TestProbe()
        val sentinelProbe = TestProbe()

        val sentinel =
          system.actorOf(Sentinel(sentinels = Seq(Server(host, 26379)), listeners = Set(sentinelProbe.ref)))
        val brando =
          system.actorOf(RedisSentinel(master = "mymaster", sentinelClient = sentinel, listeners = Set(redisProbe.ref)))

        sentinelProbe.expectMsg(Connecting(host, 26379))
        sentinelProbe.expectMsg(Connected(host, 26379))

        redisProbe.expectMsgClass(classOf[Connecting])
        redisProbe.expectMsgClass(classOf[Connected])
      }
    }

    describe("when disconnected") {
      it("should recreate a connection using sentinel") {
        val redisProbe    = TestProbe()
        val sentinelProbe = TestProbe()

        val sentinel =
          system.actorOf(Sentinel(sentinels = Seq(Server(host, 26379)), listeners = Set(sentinelProbe.ref)))
        val brando =
          system.actorOf(RedisSentinel(master = "mymaster", sentinelClient = sentinel, listeners = Set(redisProbe.ref)))

        sentinelProbe.expectMsg(Connecting(host, 26379))
        sentinelProbe.expectMsg(Connected(host, 26379))
        redisProbe.expectMsgClass(classOf[Connecting])
        redisProbe.expectMsgClass(classOf[Connected])

        brando ! Disconnected(host, 6379)

        redisProbe.expectMsg(Disconnected(host, 6379))

        redisProbe.expectMsgClass(classOf[Connecting])
        redisProbe.expectMsgClass(classOf[Connected])
      }

      it("should return a failure when disconnected") {
        val sentinel = system.actorOf(Sentinel(sentinels = Seq(Server(host, 26379))))
        val brando   = system.actorOf(RedisSentinel(master = "mymaster", sentinelClient = sentinel))

        brando ! Request("PING")

        expectMsg(Status.Failure(new RedisDisconnectedException("Disconnected from mymaster")))
      }
    }
  }
}
