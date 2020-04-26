package fs2.nats.config

import java.net.InetSocketAddress
import java.nio.file.Path

case class NatsAuthConfig(username: String, password: String)
case class NatsKeyStoreConfig(keyStoreFile: Path, storePassword: String, keyPassword: String)
case class NatsTLSConfig(keyStore: Option[NatsKeyStoreConfig], useDefault: Boolean)
case class NatsConfig(addr: InetSocketAddress,
                      auth: NatsAuthConfig,
                      tlsConfig: Option[NatsTLSConfig])
