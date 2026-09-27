package com.newtermux.features;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.termux.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Manages speech-to-text input for NewTermux terminal sessions.
 * Integrates with Android's SpeechRecognizer API for offline/online recognition.
 */
public class SpeechInputManager {

    private static final String TAG = "SpeechInputManager";

    public interface SpeechCallback {
        void onResult(String text);
        void onError(String error);
        void onListeningStarted();
        void onListeningStopped();
    }

    private final Context mContext;
    private SpeechRecognizer mSpeechRecognizer;
    private SpeechCallback mCallback;
    private boolean mIsListening = false;

    public SpeechInputManager(Context context) {
        mContext = context;
    }

    public static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void setCallback(SpeechCallback callback) {
        mCallback = callback;
    }

    public void startListening() {
        if (mIsListening) return;

        if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
            if (mCallback != null) mCallback.onError(mContext.getString(R.string.speech_recognition_unavailable));
            return;
        }

        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                mIsListening = true;
                if (mCallback != null) mCallback.onListeningStarted();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                mIsListening = false;
                if (mCallback != null) mCallback.onListeningStopped();
            }
            @Override public void onError(int error) {
                mIsListening = false;
                String msg = speechErrorToString(error);
                Log.e(TAG, "Speech error: " + msg);
                if (mCallback != null) mCallback.onError(msg);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    Log.d(TAG, "Speech result: " + text);
                    if (mCallback != null) mCallback.onResult(text);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, mContext.getString(R.string.speech_prompt));

        mSpeechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (mSpeechRecognizer != null && mIsListening) {
            mSpeechRecognizer.stopListening();
            mIsListening = false;
        }
    }

    public boolean isListening() {
        return mIsListening;
    }

    public void destroy() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
        mIsListening = false;
    }

    private String speechErrorToString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return mContext.getString(R.string.speech_error_audio);
            case SpeechRecognizer.ERROR_CLIENT: return mContext.getString(R.string.speech_error_client);
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return mContext.getString(R.string.speech_error_permission);
            case SpeechRecognizer.ERROR_NETWORK: return mContext.getString(R.string.speech_error_network);
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return mContext.getString(R.string.speech_error_network_timeout);
            case SpeechRecognizer.ERROR_NO_MATCH: return mContext.getString(R.string.speech_error_no_match);
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return mContext.getString(R.string.speech_error_busy);
            case SpeechRecognizer.ERROR_SERVER: return mContext.getString(R.string.speech_error_server);
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return mContext.getString(R.string.speech_error_timeout);
            default: return mContext.getString(R.string.speech_error_unknown, error);
        }
    }
}
