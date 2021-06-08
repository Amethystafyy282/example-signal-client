package com.example.examplesignalclient.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.examplesignalclient.databinding.FragmentHomeBinding
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.libsignal.util.Medium
import java.nio.charset.StandardCharsets
import java.util.*

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // ========== ADDRESS ===========
    private val aliceAddress = SignalProtocolAddress("+6879111111", 1)
    private val bobAddress = SignalProtocolAddress("+6879222222", 1)
    // ========== END ADDRESS ===========

    private val aliceSignedPreKey = Curve.generateKeyPair()
    private val bobSignedPreKey = Curve.generateKeyPair()

    private val aliceSignedPreKeyId = Random().nextInt(Medium.MAX_VALUE)
    private val bobSignedPreKeyId = Random().nextInt(Medium.MAX_VALUE)

    val aliceStore: SignalProtocolStore = InMemorySignalProtocolStore(
        generateIdentityKeyPair(),
        KeyHelper.generateRegistrationId(false)
    )
    val bobStore: SignalProtocolStore = InMemorySignalProtocolStore(
        generateIdentityKeyPair(),
        KeyHelper.generateRegistrationId(false)
    )

    val alicePreKeyBundle: PreKeyBundle = createAlicePreKeyBundle(aliceStore)
    val bobPreKeyBundle: PreKeyBundle = createBobPreKeyBundle(bobStore)

    val aliceSessionBuilder: SessionBuilder = SessionBuilder(
        aliceStore,
        bobAddress
    )
    val bobSessionBuilder: SessionBuilder = SessionBuilder(
        bobStore,
        aliceAddress
    )

    val aliceSessionCipher: SessionCipher = SessionCipher(
        aliceStore,
        bobAddress
    )
    val bobSessionCipher: SessionCipher = SessionCipher(
        bobStore,
        aliceAddress
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.v("BEGIN","====================================== HALO E2E EXAMPLE ======================================")

        aliceSessionBuilder.process(bobPreKeyBundle)
        bobSessionBuilder.process(alicePreKeyBundle)

        val message1 = "Hello world! Welcome to HALO E2E Example!"
        val first = encrypt(message1, aliceSessionCipher)
        val deFirst = decrypt(first, bobSessionCipher)
        Log.v("COMPARE", "IS Enc and DeC equal: ${message1 == deFirst}")

        val message2 = "Is this E2E actually working???"
        val second = encrypt(message2, bobSessionCipher)
        val deSecond = decrypt(second, aliceSessionCipher)
        Log.v("COMPARE", "IS Enc and DeC equal: ${message2 == deSecond}")

        val message3 = "I dont know. Why dont you go and try it out?"
        val third = encrypt(message3, aliceSessionCipher)
        val deThird = decrypt(third, bobSessionCipher)
        Log.v("COMPARE", "IS Enc and DeC equal: ${message3 == deThird}")

        val message4 = "That's a wonderful idea. But this look so easy. What else could it be?"
        val fourth = encrypt(message4, bobSessionCipher)
        val deFourth = decrypt(fourth, aliceSessionCipher)
        Log.v("COMPARE", "IS Enc and DeC equal: ${message4 == deFourth}")

        val message5 = "Save it for later. This is just the beginning!"
        val fifth = encrypt(message5, aliceSessionCipher)
        val deFifth = decrypt(fifth, bobSessionCipher)
        Log.v("COMPARE", "IS Enc and DeC equal: ${message4 == deFifth}")

        Log.v("END","====================================== HALO E2E EXAMPLE ======================================")
    }

    fun encrypt(message: String, cipher: SessionCipher): CiphertextMessage {
        Log.v("Encryption", "Send: $message")

        val encrypted = cipher.encrypt(message.toByteArray())
        Log.v("Encryption", "Encrypted: ${String(encrypted.serialize())}")

        return encrypted;
    }

    fun decrypt(message: CiphertextMessage, cipher: SessionCipher): String {
        val serialized = message.serialize()
        Log.v("Decryption", "Received: ${String(serialized)}")

        lateinit var decrypted: ByteArray;

        if (message.type == CiphertextMessage.PREKEY_TYPE) {
            decrypted = cipher.decrypt(PreKeySignalMessage(serialized))
        } else if (message.type == CiphertextMessage.WHISPER_TYPE) {
            decrypted = cipher.decrypt(SignalMessage(serialized))
        } else {
            Log.v("Decryption", "${String(decrypted)} is not CiphertextMessage, can't decrypt!")
        }

        Log.v("Decryption", "Decrypted: ${String(decrypted)}")

        return String(decrypted);
    }

    private fun createAlicePreKeyBundle(aliceStore: SignalProtocolStore): PreKeyBundle {
        val aliceUnsignedPreKey = Curve.generateKeyPair()
        val aliceUnsignedPreKeyId = Random().nextInt(Medium.MAX_VALUE)
        val aliceSignature = Curve.calculateSignature(
            aliceStore.identityKeyPair.privateKey, aliceSignedPreKey.publicKey.serialize()
        )

        val alicePreKeyBundle = PreKeyBundle(
            KeyHelper.generateRegistrationId(false),
            1,
            aliceUnsignedPreKeyId,
            aliceUnsignedPreKey.publicKey,
            aliceSignedPreKeyId,
            aliceSignedPreKey.publicKey,
            aliceSignature,
            aliceStore.identityKeyPair.publicKey
        )

        aliceStore.storeSignedPreKey(
            aliceSignedPreKeyId,
            SignedPreKeyRecord(
                aliceSignedPreKeyId,
                System.currentTimeMillis(),
                aliceSignedPreKey,
                aliceSignature
            )
        )
        aliceStore.storePreKey(
            aliceUnsignedPreKeyId,
            PreKeyRecord(aliceUnsignedPreKeyId, aliceUnsignedPreKey)
        )

        return alicePreKeyBundle
    }

    private fun createBobPreKeyBundle(bobStore: SignalProtocolStore): PreKeyBundle {
        val bobUnsignedPreKey = Curve.generateKeyPair()
        val bobUnsignedPreKeyId = Random().nextInt(Medium.MAX_VALUE)
        val bobSignature = Curve.calculateSignature(
            bobStore.identityKeyPair.privateKey,
            bobSignedPreKey.publicKey
                .serialize()
        )

        val bobPreKeyBundle = PreKeyBundle(
            KeyHelper.generateRegistrationId(false),
            1,
            bobUnsignedPreKeyId,
            bobUnsignedPreKey.publicKey,
            bobSignedPreKeyId,
            bobSignedPreKey.publicKey,
            bobSignature,
            bobStore.identityKeyPair.publicKey
        )

        bobStore.storeSignedPreKey(
            bobSignedPreKeyId,
            SignedPreKeyRecord(
                bobSignedPreKeyId,
                System.currentTimeMillis(),
                bobSignedPreKey,
                bobSignature
            )
        )
        bobStore.storePreKey(
            bobUnsignedPreKeyId,
            PreKeyRecord(bobUnsignedPreKeyId, bobUnsignedPreKey)
        )

        return bobPreKeyBundle
    }

    private fun generateIdentityKeyPair(): IdentityKeyPair {
        val identityKeyPairKeys = Curve.generateKeyPair()
        return IdentityKeyPair(
            IdentityKey(identityKeyPairKeys.publicKey),
            identityKeyPairKeys.privateKey
        )
    }

}