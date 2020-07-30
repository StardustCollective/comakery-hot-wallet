package org.constellation.cmhotwallet

import java.io.{File, FileInputStream, FileOutputStream}
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, KeyStore, PrivateKey, Provider, PublicKey, SecureRandom, Signature}
import java.util.{Calendar, Date}

import cats.data.EitherT
import cats.effect.{Resource, Sync}
import cats.implicits._
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.constellation.cmhotwallet.Ed25519KeyTool.{Ed25519, storeExtension, storeType}
import org.constellation.keytool.KeyUtils.base64

class Ed25519KeyTool {
  private val provider = new BouncyCastleProvider()

  private def reader[F[_]: Sync](keyStorePath: String): Resource[F, FileInputStream] =
    Resource.fromAutoCloseable(Sync[F].delay {
      new FileInputStream(keyStorePath)
    })

  private def writer[F[_]: Sync](keyStorePath: String): Resource[F, FileOutputStream] =
    Resource.fromAutoCloseable(Sync[F].delay {
      val file = new File(keyStorePath)
      if (file.exists()) throw new RuntimeException("KeyStore file already exists!")
      new FileOutputStream(file)
    })

  private def withExtension(path: String): String =
    if (path.endsWith(storeExtension)) path else s"$path.$storeExtension"

  def generateAndStoreKeyPair[F[_]: Sync](
    path: String,
    alias: String,
    storePassword: Array[Char],
    keyPassword: Array[Char]
  ): EitherT[F, Throwable, Unit] =
    for {
      keyPair <- Sync[F].delay(createKeyPair()).attemptT
      keyStore <- writer(withExtension(path))
        .use(
          stream =>
            for {
              keyStore <- Sync[F].delay(
                KeyStore.getInstance(storeType, provider)
              )
              _ <- Sync[F].delay(
                keyStore.load(null, storePassword)
              )
              certificate = generateCertificate(keyPair, 365000, Ed25519)
              _ <- Sync[F].delay {
                keyStore.setKeyEntry(alias, keyPair.getPrivate, keyPassword, Array(certificate))
              }
              _ <- Sync[F].delay {
                keyStore.store(stream, storePassword)
              }
            } yield keyStore
        )
        .attemptT
    } yield ()

  private def generateCertificate(pair: KeyPair, days: Int, algorithm: String): X509Certificate = {
    def periodFromDays(days: Int): (Date, Date) = {
      val notBefore = new Date()
      val calendar = Calendar.getInstance
      calendar.setTime(notBefore)
      calendar.add(Calendar.DAY_OF_MONTH, days)
      val notAfter = calendar.getTime
      (notBefore, notAfter)
    }

    val dn = "CN=constellationnetwork.io,O=Constellation Labs"
    val owner = new X500Name(dn)
    val serial = new BigInteger(64, new SecureRandom())
    val (notBefore, notAfter) = periodFromDays(days)
    val publicKeyInfo: SubjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(pair.getPublic.getEncoded)
    val builder = new X509v3CertificateBuilder(owner, serial, notBefore, notAfter, owner, publicKeyInfo)
    val signer = new JcaContentSignerBuilder(algorithm).setProvider(provider).build(pair.getPrivate)
    val certificateHolder = builder.build(signer)
    val selfSignedCertificate = new JcaX509CertificateConverter().getCertificate(certificateHolder)

    selfSignedCertificate
  }

  def openKeyStoreAndGetKeyPair[F[_]: Sync](
    path: String,
    alias: String,
    storepass: Array[Char],
    keypass: Array[Char]
  ): EitherT[F, Throwable, KeyPair] =
    reader(path)
      .use(
        stream =>
          for {
            keyStore <- Sync[F].delay(KeyStore.getInstance(storeType, provider))
            _ <- Sync[F].delay(keyStore.load(stream, storepass))
            privateKey = keyStore.getKey(alias, keypass).asInstanceOf[PrivateKey]
            publicKey = keyStore.getCertificate(alias).getPublicKey
          } yield new KeyPair(publicKey, privateKey)
      )
      .attemptT

  def createKeyPair(): KeyPair = {
    val keyGen: KeyPairGenerator = KeyPairGenerator.getInstance(Ed25519, provider)
    keyGen.generateKeyPair()
  }

  def sign(msg: String)(privateKey: PrivateKey): String = {
    val signer = Signature.getInstance(Ed25519, provider)
    signer.initSign(privateKey)
    signer.update(msg.getBytes(), 0, msg.getBytes.length)
    val signature = signer.sign()
    base64(signature)
  }
}

object Ed25519KeyTool {
  val Ed25519 = "Ed25519"
  val PublicKeyPrefix = "MCowBQYDK2VwAyEA"
  val storeType: String = "PKCS12"
  val storeExtension: String = "p12"

  def apply() = new Ed25519KeyTool()

  def publicKeyToBase64(publicKey: PublicKey): String =
    base64(publicKey.getEncoded)
      .stripPrefix(PublicKeyPrefix)
}
