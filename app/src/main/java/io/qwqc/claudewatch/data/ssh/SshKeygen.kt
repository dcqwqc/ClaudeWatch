package io.qwqc.claudewatch.data.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

/**
 * Generates an SSH key pair on the watch so the user never has to type or paste
 * a PEM. The private key is kept in app settings; the public key is shown once
 * so it can be added to the server's ~/.ssh/authorized_keys.
 */
object SshKeygen {

    data class Generated(val privatePem: String, val publicOpenSsh: String)

    fun generate(comment: String = "claude-watch"): Generated {
        val jsch = JSch()
        // Prefer ed25519 (short, modern); fall back to RSA if unavailable.
        val kp = runCatching { KeyPair.genKeyPair(jsch, KeyPair.ED25519) }
            .getOrElse { KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072) }

        val priv = ByteArrayOutputStream().also { kp.writePrivateKey(it) }
        val pub = ByteArrayOutputStream().also { kp.writePublicKey(it, comment) }
        kp.dispose()
        return Generated(priv.toString("UTF-8"), pub.toString("UTF-8").trim())
    }
}
