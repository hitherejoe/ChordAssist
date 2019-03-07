package com.example

import com.google.actions.api.ActionContext
import com.google.actions.api.ActionRequest
import com.google.actions.api.ActionResponse
import com.google.actions.api.Capability
import com.google.actions.api.DialogflowApp
import com.google.actions.api.ForIntent
import com.google.api.services.actions_fulfillment.v2.model.BasicCard
import com.google.api.services.actions_fulfillment.v2.model.Image
import com.google.api.services.actions_fulfillment.v2.model.MediaObject
import com.google.api.services.actions_fulfillment.v2.model.MediaResponse
import com.google.api.services.actions_fulfillment.v2.model.SimpleResponse
import com.google.api.services.actions_fulfillment.v2.model.Suggestion
import com.google.api.services.actions_fulfillment.v2.model.TableCard
import com.google.api.services.actions_fulfillment.v2.model.TableCardCell
import com.google.api.services.actions_fulfillment.v2.model.TableCardColumnProperties
import com.google.api.services.actions_fulfillment.v2.model.TableCardRow
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import org.slf4j.LoggerFactory
import java.util.*

class ChordAssistApp : DialogflowApp() {

    private val LOGGER = LoggerFactory.getLogger(ChordAssistApp::class.java)

    val chords = arrayOf("E", "A", "D", "G", "B", "E")

    companion object {
        const val PARAMETER_CHORD = "chord"
        const val PARAMETER_NOTE = "note"
        const val COLLECTION_CHORDS = "chords"
        const val COLLECTION_NOTES = "notes"
        const val FIELD_DISPLAY_NAME = "display_name"
        const val FIELD_PACK = "pack"
        const val FIELD_NAME = "name"
        const val FIELD_AUDIO = "audio"
        const val FIELD_PATTERN = "pattern"
        const val CONTEXT_NOTE_FOLLOWUP = "playnote-followup"
        const val CONTEXT_LEARN_CHORD_FOLLOWUP = "learnchord-followup"
    }

    @ForIntent("learn.chord")
    fun learnChord(request: ActionRequest): ActionResponse {
        return handleChordRequest(request, request.getParameter(PARAMETER_CHORD) as String? ?: "")
    }

    @ForIntent("learn.chord - repeat")
    fun repeatChord(request: ActionRequest): ActionResponse {
        return handleChordRequest(request,
            request.getContext(CONTEXT_LEARN_CHORD_FOLLOWUP)?.parameters?.get(
                PARAMETER_CHORD) as String)
    }

    @ForIntent("learn.chord - fallback")
    fun learnChordFallback(request: ActionRequest): ActionResponse {
        return handleChordRequest(request, request.getArgument(PARAMETER_CHORD)?.name ?: "")
    }

    @ForIntent("available.chords")
    fun showAvailableChords(request: ActionRequest): ActionResponse {
        val responseBuilder = getResponseBuilder(request)
        val query = getDatabase().collection(COLLECTION_CHORDS).get().get()
        val documents = query.documents
        val rows = mutableListOf<TableCardRow>()
        var text = ""
        documents.forEach {
            val displayName = it.getString(FIELD_DISPLAY_NAME)
            text += "$displayName, "
            rows.add(TableCardRow()
                .setCells(listOf(
                    TableCardCell().setText(displayName),
                    TableCardCell().setText(it.getString(FIELD_PACK)))
                ))
        }

        text = text.substring(0, text.length - 2)
        val response = getResource("available_chords_response") + text

        responseBuilder.add(
            SimpleResponse().setDisplayText(response).setTextToSpeech(response))

        if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
            responseBuilder.add(
                TableCard()
                    .setTitle(getResource("available_chords_table_title"))
                    .setSubtitle(getResource("available_chords_table_description"))
                    .setColumnProperties(
                        listOf(TableCardColumnProperties().setHeader(
                            getResource("available_chords_table_chord_header")),
                            TableCardColumnProperties().setHeader(
                                getResource("available_chords_table_pack_header"))))
                    .setRows(rows)
            )
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_teach_me_a_chord")))
        }
        return responseBuilder.build()
    }

    @ForIntent("available.tasks")
    fun availableTasks(request: ActionRequest): ActionResponse {
        val responseBuilder = getResponseBuilder(request)
        val response = getResource("available_tasks_response")
        responseBuilder.add(
            SimpleResponse().setDisplayText(response).setTextToSpeech(response))

        if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_teach_me_a_chord")))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_available_chords")))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_tune_guitar")))
        }
        return responseBuilder.build()
    }

    @ForIntent("welcome")
    fun welcome(request: ActionRequest): ActionResponse {
        val responseBuilder = getResponseBuilder(request)
        val response = getResource("welcome_response")
        responseBuilder.add(
            SimpleResponse().setDisplayText(response).setTextToSpeech(response))

        if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_teach_me_a_chord")))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_available_chords")))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_tune_guitar")))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_what_can_you_do")))
        }
        return responseBuilder.build()
    }

    @ForIntent("play.note")
    fun playNote(request: ActionRequest): ActionResponse {
        val responseBuilder = getResponseBuilder(request)
        if (!request.hasCapability(Capability.MEDIA_RESPONSE_AUDIO.value)) {
            val response = getResource("error_audio_playback")
            responseBuilder.add(
                SimpleResponse().setDisplayText(response).setTextToSpeech(response)
            )
            return responseBuilder.build()
        }

        val chord = request.getParameter(PARAMETER_NOTE) as String
        val document = getDatabase().collection(COLLECTION_NOTES).document(chord).get().get()
        val input = document?.get(FIELD_NAME)
        val inputResponse = getResource("play_note_title").format(input)
        responseBuilder.add(
            SimpleResponse().setDisplayText(inputResponse).setTextToSpeech(inputResponse)
        )
        val audioResponse = document?.get(FIELD_AUDIO)
        responseBuilder.add(
            MediaResponse()
                .setMediaType("AUDIO")
                .setMediaObjects(
                    listOf(
                        MediaObject()
                            .setName(inputResponse)
                            .setContentUrl(audioResponse as String)
                    )
                )
        )
        if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_play_another_note")))
        }

        return responseBuilder.build()
    }

    @ForIntent("handle.finish.audio")
    fun handleFinishAudio(request: ActionRequest): ActionResponse {
        val note = request.getContext(CONTEXT_NOTE_FOLLOWUP)?.parameters
            ?.get(PARAMETER_NOTE) as String
        val responseBuilder = getResponseBuilder(request)
        val inputResponse = getResource("audio_completion_response")
        responseBuilder.add(
            SimpleResponse().setDisplayText(inputResponse).setTextToSpeech(inputResponse)
        )

        if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
            responseBuilder.add(Suggestion().setTitle("Repeat $note"))
            responseBuilder.add(Suggestion()
                .setTitle(getResource("suggestion_play_another_note")))
        }
        return responseBuilder.build()
    }

    private fun getDatabase(): com.google.cloud.firestore.Firestore {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentials = GoogleCredentials.getApplicationDefault()
            val options = FirebaseOptions.Builder()
                .setCredentials(credentials)
                .setProjectId("your_project_id")
                .build()
            FirebaseApp.initializeApp(options)
        }
        return FirestoreClient.getFirestore()
    }

    private fun handleChordRequest(request: ActionRequest, chord: String): ActionResponse {
        val responseBuilder = getResponseBuilder(request)
        if (chord.isNotEmpty()) {
            val document = getDatabase().collection(COLLECTION_CHORDS).document(chord).get().get()
            val chordInstructions = buildString(document?.getString(FIELD_PATTERN) ?: "") + ". "
            responseBuilder.add(
                SimpleResponse()
                    .setDisplayText(chordInstructions)
                    .setTextToSpeech(chordInstructions)
            )
            if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
                responseBuilder.add(
                    BasicCard()
                        .setTitle(getResource("learn_chord_title").format(chord))
                        .setImage(
                            Image()
                                .setUrl(document.getString("image") ?: "")
                                .setAccessibilityText(
                                    getResource("learn_chord_title").format(chord))
                        )
                )

                responseBuilder.add(Suggestion()
                    .setTitle(getResource("suggestion_repeat")))
                responseBuilder.add(Suggestion()
                    .setTitle(getResource("suggestion_teach_another")))
            }
            return responseBuilder.build()
        } else {
            responseBuilder.add(ActionContext(CONTEXT_LEARN_CHORD_FOLLOWUP, 5))
            val response = getResource("learn_chord_unknown_response")
            responseBuilder.add(
                SimpleResponse().setDisplayText(response).setTextToSpeech(response)
            )
            if (request.hasCapability(Capability.SCREEN_OUTPUT.value)) {
                responseBuilder.add(Suggestion().setTitle("Show me available chords"))
            }
        }
        return responseBuilder.build()
    }

    private fun buildString(sequence: String): String {
        var chordSequence = ""
        for (index in sequence.indices) {
            var note = chords[index] + " " + buildNote(sequence[index].toString())
            if (sequence[index] != 'X' && sequence[index] != '0') note += " " + sequence[index]
            if (index < sequence.length - 1) note += ", "
            chordSequence += note
        }

        return chordSequence
    }

    private fun buildNote(note: String): String {
        return when (note) {
            "X" -> getResource("label_muted")
            "0" -> getResource("label_open")
            else -> getResource("label_fret")
        }
    }

    private fun getResource(label: String): String {
        return ResourceBundle.getBundle("resources").getString(label)
    }
}