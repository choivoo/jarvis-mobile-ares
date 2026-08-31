package com.sundwaeji.jarvis.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** On-device KO ⇄ EN translation. Models are downloaded by ML Kit only when first needed. */
class TranslationManager {
    private val koToEn = translator(TranslateLanguage.KOREAN, TranslateLanguage.ENGLISH)
    private val enToKo = translator(TranslateLanguage.ENGLISH, TranslateLanguage.KOREAN)

    fun translateKoToEn(text: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) =
        translate(koToEn, text, onSuccess, onFailure)

    fun translateEnToKo(text: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) =
        translate(enToKo, text, onSuccess, onFailure)

    fun close() {
        koToEn.close()
        enToKo.close()
    }

    private fun translator(source: String, target: String): Translator =
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())

    private fun translate(
        translator: Translator,
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener { onFailure("번역을 완료하지 못했습니다. 네트워크를 확인한 뒤 다시 시도하세요.") }
            }
            .addOnFailureListener { onFailure("번역 모델을 준비하지 못했습니다. 인터넷 연결을 확인해 주세요.") }
    }
}
