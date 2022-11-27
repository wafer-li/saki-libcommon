package com.serenegiant.media;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2022 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import java.nio.ByteBuffer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

/**
 * AudioRecordを使って音声データを取得し、登録したコールバックへ分配するためのクラス
 * 同じ音声入力ソースに対して複数のAudioRecordを生成するとエラーになるのでシングルトン的にアクセス出来るようにするため
 */
public class AudioSampler extends IAudioSampler {
	private static final boolean DEBUG = false; // set false on production
	private static final String TAG = AudioSampler.class.getSimpleName();

	@NonNull
	private final Object mSync = new Object();
	@Nullable
	private AudioThread mAudioThread;
    private final int AUDIO_SOURCE;
    private final int SAMPLING_RATE, CHANNEL_COUNT;
	/**
	 * AudioRecordから1度に読み込みを試みる最大バイト数
	 */
	private final int SAMPLES_PER_FRAME;
	/**
	 * AudioRecord初期化時に使うバッファのサイズ
	 */
	private final int BUFFER_SIZE;
	private final boolean FORCE_SOURCE;

	/**
	 * コンストラクタ
	 * @param audioSource 音声ソース, MediaRecorder.AudioSourceのどれか
	 *     ただし、一般アプリで利用できないVOICE_UPLINK(2)はCAMCORDER(5)へ
	 *     VOICE_DOWNLINK(3)はVOICE_COMMUNICATION(7)
	 *     VOICE_CALL(4)はMIC(1)へ置換する
	 * @param channelNum
	 * @param samplingRate
	 * @param samplesPerFrame
	 * @param framesPerBuffer
	 */
	public AudioSampler(final int audioSource, final int channelNum,
		final int samplingRate, final int samplesPerFrame, final int framesPerBuffer) {

		this(audioSource, channelNum, samplingRate, samplesPerFrame, framesPerBuffer, false);
	}

	/**
	 * コンストラクタ
	 * @param audioSource 音声ソース, MediaRecorder.AudioSourceのどれか
	 *     ただし、一般アプリで利用できないVOICE_UPLINK(2)はCAMCORDER(5)へ
	 *     VOICE_DOWNLINK(3)はVOICE_COMMUNICATION(7)
	 *     VOICE_CALL(4)はMIC(1)へ置換する
	 * @param channelNum 音声チャネル数, 1 or 2
	 * @param samplingRate サンプリングレート
	 * @param samplesPerFrame 1フレーム辺りのサンプル数
	 * @param framesPerBuffer バッファ辺りのフレーム数
	 * @param forceSource 音声ソースを強制するかどうか, falseなら利用可能な音声ソースを順に試す
	 */
	public AudioSampler(final int audioSource, final int channelNum,
		final int samplingRate, final int samplesPerFrame, final int framesPerBuffer,
		final boolean forceSource) {

		super();
//		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		// パラメータを保存
		AUDIO_SOURCE = audioSource;
		CHANNEL_COUNT = channelNum;
		SAMPLING_RATE = samplingRate;
		SAMPLES_PER_FRAME = samplesPerFrame * channelNum;
		BUFFER_SIZE = AudioRecordCompat.getAudioBufferSize(
			channelNum, AudioRecordCompat.AUDIO_FORMAT,
			samplingRate, samplesPerFrame, framesPerBuffer);
		FORCE_SOURCE = forceSource;
	}

	/**
	 * 音声データ１つ当たりのバイト数を返す
	 * (AudioRecordから1度に読み込みを試みる最大バイト数)
	 * @return
	 */
	@Override
	public int getBufferSize() {
		return SAMPLES_PER_FRAME;
	}

	/**
	 * 音声データサンプリング開始
	 * 実際の処理は別スレッド上で実行される
	 */
	@RequiresPermission(Manifest.permission.RECORD_AUDIO)
	@Override
	public synchronized void start() {
//		if (DEBUG) Log.v(TAG, "start:mIsCapturing=" + mIsCapturing);
		super.start();
		synchronized (mSync) {
			if (mAudioThread == null) {
				init_pool(SAMPLES_PER_FRAME);
				// 内蔵マイクからの音声取り込みスレッド生成＆実行
				mAudioThread = new AudioThread();
				mAudioThread.start();
			}
		}
	}

	/**
	 * 音声データのサンプリングを停止させる
	 */
	@Override
	public synchronized void stop() {
//		if (DEBUG) Log.v(TAG, "stop:mIsCapturing=" + mIsCapturing);
		synchronized (mSync) {
			mIsCapturing = false;
			mAudioThread = null;
			mSync.notify();
		}
		super.stop();
	}

	@Override
	public int getAudioSource() {
		return AUDIO_SOURCE;
	}

	protected static final class AudioRecordRec {
		AudioRecord audioRecord;
		int bufferSize;
	}

	/**
	 * AudioRecordから無圧縮PCM16bitで内蔵マイクからの音声データを取得してキューへ書き込むためのスレッド
	 */
    private final class AudioThread extends Thread {

    	public AudioThread() {
    		super("AudioThread");
    	}

		@SuppressLint("MissingPermission")
    	@Override
    	public void run() {
//    		if (DEBUG) Log.v(TAG, "AudioThread:start");
    		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO); // THREAD_PRIORITY_URGENT_AUDIO
//    		if (DEBUG) Log.v(TAG, getName() + " started");
/*			final Class audioSystemClass = Class.forName("android.media.AudioSystem");
			// will disable the headphone
			setDeviceConnectionState.Invoke(audioSystemClass, (Integer)DEVICE_OUT_WIRED_HEADPHONE, (Integer)DEVICE_STATE_UNAVAILABLE, new String(""));
			// will enable the headphone
			setDeviceConnectionState.Invoke(audioSystemClass, (Integer)DEVICE_OUT_WIRED_HEADPHONE, (Integer)DEVICE_STATE_AVAILABLE, new Lang.String(""));
*/
			int retry = 3;
RETRY_LOOP:	for ( ; mIsCapturing && (retry > 0) ; ) {
				@AudioRecordCompat.AudioChannel
				final int audioChannel = AudioRecordCompat.getAudioChannel(CHANNEL_COUNT);
				AudioRecord audioRecord;
				if (FORCE_SOURCE) {
					try {
						audioRecord = AudioRecordCompat.newInstance(
							AUDIO_SOURCE, SAMPLING_RATE, audioChannel, AudioRecordCompat.AUDIO_FORMAT, BUFFER_SIZE);
					} catch (final Exception e) {
						Log.d(TAG, "AudioThread:", e);
						audioRecord = null;
					}
				} else {
					audioRecord = AudioRecordCompat.createAudioRecord(
						AUDIO_SOURCE, SAMPLING_RATE, audioChannel, AudioRecordCompat.AUDIO_FORMAT, BUFFER_SIZE);
				}
				int err_count = 0;
				if (audioRecord != null) {
					try {
						if (mIsCapturing) {
//		        			if (DEBUG) Log.v(TAG, "AudioThread:start audio recording");
							int readBytes;
							ByteBuffer buffer;
							audioRecord.startRecording();
							try {
								RecycleMediaData data;
LOOP:							for ( ; mIsCapturing ;) {
									data = obtain();
									if (data != null) {
										// check recording state
										final int recordingState = audioRecord.getRecordingState();
										if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
											if (err_count == 0) {
												Log.e(TAG, "not a recording state," + recordingState);
											}
											err_count++;
											data.recycle();
											if (err_count > 20) {
												retry--;
												break LOOP;
											} else {
												synchronized (mSync) {
													mSync.wait(100);
												}
												continue;
											}
										}
										// try to read audio data
										buffer = data.get();
										buffer.clear();
										// 1回に読み込むのはSAMPLES_PER_FRAMEバイト
										try {
											readBytes = audioRecord.read(buffer, SAMPLES_PER_FRAME);
										} catch (final Exception e) {
											Log.e(TAG, "AudioRecord#read failed:" + e);
											err_count++;
											retry--;
											data.recycle();
											callOnError(e);
											break LOOP;
										}
										if (readBytes > 0) {
											// 正常に読み込めた時
											err_count = 0;
											// FIXME ここはMediaDataのセッターで一括でセットするように変更する
											data.presentationTimeUs(getInputPTSUs())
												.size(readBytes);
											buffer.position(readBytes);
											buffer.flip();
											// 音声データキューに追加する
											addMediaData(data);
											continue;
										} else if (readBytes == AudioRecord.SUCCESS) {	// == 0
											err_count = 0;
											data.recycle();
											continue;
										} else if (readBytes == AudioRecord.ERROR) {
											if (err_count == 0) {
												Log.e(TAG, "Read error ERROR");
											}
										} else if (readBytes == AudioRecord.ERROR_BAD_VALUE) {
											if (err_count == 0) {
												Log.e(TAG, "Read error ERROR_BAD_VALUE");
											}
										} else if (readBytes == AudioRecord.ERROR_INVALID_OPERATION) {
											if (err_count == 0) {
												Log.e(TAG, "Read error ERROR_INVALID_OPERATION");
											}
										} else if (readBytes == AudioRecord.ERROR_DEAD_OBJECT) {
											Log.e(TAG, "Read error ERROR_DEAD_OBJECT");
											err_count++;
											retry--;
											data.recycle();
											break LOOP;
										} else if (readBytes < 0) {
											if (err_count == 0) {
												Log.e(TAG, "Read returned unknown err " + readBytes);
											}
										}
										err_count++;
										data.recycle();
									} // end of if (data != null)
									if (err_count > 10) {
										retry--;
										break LOOP;
									}
								} // end of for ( ; mIsCapturing ;)
							} finally {
								audioRecord.stop();
							}
						}
					} catch (final Exception e) {
//	        			Log.w(TAG, "exception on AudioRecord:", e);
						retry--;
						callOnError(e);
					} finally {
						audioRecord.release();
					}
					if (mIsCapturing && (err_count > 0) && (retry > 0)) {
						// キャプチャリング中でエラーからのリカバリー処理が必要なときは0.5秒待機
						for (int i = 0; mIsCapturing && (i < 5); i++) {
							synchronized (mSync) {
								try {
									mSync.wait(100);
								} catch (final InterruptedException e) {
									break RETRY_LOOP;
								}
							}
						}
					}
				} else {
//        			Log.w(TAG, "AudioRecord failed to initialize");
					callOnError(new RuntimeException("AudioRecord failed to initialize"));
					retry = 0;	// 初期化できんかったときはリトライしない
				}
			}	// end of for
			AudioSampler.this.stop();
//    		if (DEBUG) Log.v(TAG, "AudioThread:finished");
    	} // #run
    }

	@Override
	public int getChannels() {
		return CHANNEL_COUNT;
	}

	@Override
	public int getSamplingFrequency() {
		return SAMPLING_RATE;
	}

	@Override
	public int getBitResolution() {
		return 16;	// AudioFormat.ENCODING_PCM_16BIT
	}

}
