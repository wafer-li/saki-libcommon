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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.system.BuildCheck;
import com.serenegiant.utils.BufferHelper;

/**
 * MediaCodec関係のユーティリティ関数を集めたクラス
 */
public final class MediaCodecUtils {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = MediaCodecUtils.class.getSimpleName();

	private static final long TIMEOUT_USEC = 10000L;	// 10ミリ秒

	private MediaCodecUtils() {
		// インスタンス化をエラーにするためにデフォルトコンストラクタをprivateに
	}

	public static final String MIME_VIDEO_AVC = "video/avc";		// h.264
	public static final String MIME_AUDIO_AAC = "audio/mp4a-latm";	// AAC
	@SuppressLint("InlinedApi")
	public static final int BUFFER_FLAG_KEY_FRAME
		= BuildCheck.isLollipop() ? MediaCodec.BUFFER_FLAG_KEY_FRAME : MediaCodec.BUFFER_FLAG_SYNC_FRAME;
	/** codec specific dataのスタートマーカー = AnnexBのスタートマーカーと同じ */
	public static final byte[] START_MARKER = BufferHelper.ANNEXB_START_MARK;

	/**
	 * 非圧縮image/videoで使用可能なカラーフォーマット
     * from OMX_IVCommon.h
	 * MediaCodecInfo.CodecCapabilities.COLOR_FormatXXXに一部のものは定義されている
     *  Unused                 : Placeholder value when format is N/A
     *  Monochrome             : black and white
     *  8bitRGB332             : Red 7:5, Green 4:2, Blue 1:0
     *  12bitRGB444            : Red 11:8, Green 7:4, Blue 3:0
     *  16bitARGB4444          : Alpha 15:12, Red 11:8, Green 7:4, Blue 3:0
     *  16bitARGB1555          : Alpha 15, Red 14:10, Green 9:5, Blue 4:0
     *  16bitRGB565            : Red 15:11, Green 10:5, Blue 4:0
     *  16bitBGR565            : Blue 15:11, Green 10:5, Red 4:0
     *  18bitRGB666            : Red 17:12, Green 11:6, Blue 5:0
     *  18bitARGB1665          : Alpha 17, Red 16:11, Green 10:5, Blue 4:0
     *  19bitARGB1666          : Alpha 18, Red 17:12, Green 11:6, Blue 5:0
     *  24bitRGB888            : Red 24:16, Green 15:8, Blue 7:0
     *  24bitBGR888            : Blue 24:16, Green 15:8, Red 7:0
     *  24bitARGB1887          : Alpha 23, Red 22:15, Green 14:7, Blue 6:0
     *  25bitARGB1888          : Alpha 24, Red 23:16, Green 15:8, Blue 7:0
     *  32bitBGRA8888          : Blue 31:24, Green 23:16, Red 15:8, Alpha 7:0
     *  32bitARGB8888          : Alpha 31:24, Red 23:16, Green 15:8, Blue 7:0
     *  YUV411Planar           : U,Y are subsampled by a factor of 4 horizontally
     *  YUV411PackedPlanar     : packed per payload in planar slices
     *  YUV420Planar           : Three arrays Y,U,V.
     *  YUV420PackedPlanar     : packed per payload in planar slices
     *  YUV420SemiPlanar       : Two arrays, one is all Y, the other is U and V
     *  YUV422Planar           : Three arrays Y,U,V.
     *  YUV422PackedPlanar     : packed per payload in planar slices
     *  YUV422SemiPlanar       : Two arrays, one is all Y, the other is U and V
     *  YCbYCr                 : Organized as 16bit YUYV (i.e. YCbYCr)
     *  YCrYCb                 : Organized as 16bit YVYU (i.e. YCrYCb)
     *  CbYCrY                 : Organized as 16bit UYVY (i.e. CbYCrY)
     *  CrYCbY                 : Organized as 16bit VYUY (i.e. CrYCbY)
     *  YUV444Interleaved      : Each pixel contains equal parts YUV
     *  RawBayer8bit           : SMIA camera output format
     *  RawBayer10bit          : SMIA camera output format
     *  RawBayer8bitcompressed : SMIA camera output format
     */
//	public static final int OMX_COLOR_FORMATTYPE = 0;
	public static final int OMX_COLOR_FormatUnused = 0;
	public static final int OMX_COLOR_FormatMonochrome = 1;
	public static final int OMX_COLOR_Format8bitRGB332 = 2;
	public static final int OMX_COLOR_Format12bitRGB444 = 3;
	public static final int OMX_COLOR_Format16bitARGB4444 = 4;
	public static final int OMX_COLOR_Format16bitARGB1555 = 5;
	public static final int OMX_COLOR_Format16bitRGB565 = 6;
	public static final int OMX_COLOR_Format16bitBGR565 = 7;
	public static final int OMX_COLOR_Format18bitRGB666 = 8;
	public static final int OMX_COLOR_Format18bitARGB1665 = 9;
	public static final int OMX_COLOR_Format19bitARGB1666 = 10;
	public static final int OMX_COLOR_Format24bitRGB888 = 11;
	public static final int OMX_COLOR_Format24bitBGR888 = 12;
	public static final int OMX_COLOR_Format24bitARGB1887 = 13;
	public static final int OMX_COLOR_Format25bitARGB1888 = 14;
	public static final int OMX_COLOR_Format32bitBGRA8888 = 15;
	public static final int OMX_COLOR_Format32bitARGB8888 = 16;
	public static final int OMX_COLOR_FormatYUV411Planar = 17;
	public static final int OMX_COLOR_FormatYUV411PackedPlanar = 18;
	public static final int OMX_COLOR_FormatYUV420Planar = 19;
	public static final int OMX_COLOR_FormatYUV420PackedPlanar = 20;
	public static final int OMX_COLOR_FormatYUV420SemiPlanar = 21;
	public static final int OMX_COLOR_FormatYUV422Planar = 22;
	public static final int OMX_COLOR_FormatYUV422PackedPlanar = 23;
	public static final int OMX_COLOR_FormatYUV422SemiPlanar = 24;
	public static final int OMX_COLOR_FormatYCbYCr = 25;
	public static final int OMX_COLOR_FormatYCrYCb = 26;
	public static final int OMX_COLOR_FormatCbYCrY = 27;
	public static final int OMX_COLOR_FormatCrYCbY = 28;
	public static final int OMX_COLOR_FormatYUV444Interleaved = 29;
	public static final int OMX_COLOR_FormatRawBayer8bit = 30;
	public static final int OMX_COLOR_FormatRawBayer10bit = 31;
	public static final int OMX_COLOR_FormatRawBayer8bitcompressed = 32;
	public static final int OMX_COLOR_FormatL2 = 33;
	public static final int OMX_COLOR_FormatL4 = 34;
	public static final int OMX_COLOR_FormatL8 = 35;
	public static final int OMX_COLOR_FormatL16 = 36;
	public static final int OMX_COLOR_FormatL24 = 37;
	public static final int OMX_COLOR_FormatL32 = 38;
	public static final int OMX_COLOR_FormatYUV420PackedSemiPlanar = 39;
	public static final int OMX_COLOR_FormatYUV422PackedSemiPlanar = 40;
	public static final int OMX_COLOR_Format18BitBGR666 = 41;
	public static final int OMX_COLOR_Format24BitARGB6666 = 42;
	public static final int OMX_COLOR_Format24BitABGR6666 = 43;
	public static final int OMX_COLOR_FormatKhronosExtensions = 0x6F000000; //< Reserved region for introducing Khronos Standard Extensions
	public static final int OMX_COLOR_FormatVendorStartUnused = 0x7F000000; //< Reserved region for introducing Vendor Extensions
	// Reserved android opaque colorformat. Tells the encoder that
	// the actual colorformat will be relayed by the Gralloc Buffers.
	// FIXME: In the process of reserving some enum values for
	// Android-specific OMX IL colorformats. Change this enum to
	// an acceptable range once that is done.
	public static final int OMX_COLOR_FormatAndroidOpaque = 0x7F000789;
	public static final int OMX_TI_COLOR_FormatYUV420PackedSemiPlanar = 0x7F000100;
	public static final int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
	public static final int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
	public static final int OMX_SEC_COLOR_FormatNV12Tiled = 0x7FC00002;
	public static final int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m = 0x7FA30C04;
	public static final int OMX_COLOR_FormatMax = 0x7FFFFFFF;

//--------------------------------------------------------------------------------
	/**
	 * 映像＆音声コーデックの情報をJSONObjectとして取得する
	 * @return
	 * @throws JSONException
	 */
	public static JSONObject get() throws JSONException {
		final JSONObject result = new JSONObject();
		try {
			result.put("VIDEO", getVideo());
		} catch (final Exception e) {
			result.put("VIDEO", e.getMessage());
		}
		try {
			result.put("AUDIO", getAudio());
		} catch (final Exception e) {
			result.put("AUDIO", e.getMessage());
		}
		return result;
	}

	/**
	 * 映像コーデックの情報をJSONObjectとして取得する
	 * @return
	 * @throws JSONException
	 */
	private static JSONObject getVideo() throws JSONException{
		final JSONObject result = new JSONObject();
    	// コーデックの一覧を取得
        final int numCodecs = getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = getCodecInfoAt(i);
        	final JSONObject codec = new JSONObject();
            final String[] types = codecInfo.getSupportedTypes();
            final int n = types.length;
            boolean isvideo = false;
            for (int j = 0; j < n; j++) {
                if (types[j].startsWith("video/")) {
                	isvideo = true;
					codec.put(Integer.toString(j), types[j]);
		    		final MediaCodecInfo.CodecCapabilities capabilities;
		    		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		        	try {
		    			capabilities = getCodecCapabilities(codecInfo, types[j]);
		        	} finally {
		        		// 元の優先度に戻す
		        		Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
		        	}
		        	try {
						final int[] colorFormats = capabilities.colorFormats;
						final int m = colorFormats != null ? colorFormats.length : 0;
						if (m > 0) {
							final JSONObject caps = new JSONObject();
							for (int k = 0; k < m; k++) {
								caps.put(String.format(Locale.US, "COLOR_FORMAT(%d)", k), getColorFormatName(colorFormats[k]));
							}
							codec.put("COLOR_FORMATS", caps);
						}
		        	} catch (final Exception e) {
		            	codec.put("COLOR_FORMATS", e.getMessage());
		        	}
		            try {
				        final MediaCodecInfo.CodecProfileLevel[] profileLevel = capabilities.profileLevels;
			        	final int m = profileLevel != null ? profileLevel.length : 0;
			        	if (m > 0) {
				        	final JSONObject profiles = new JSONObject();
					        for (int k = 0; k < m; k++) {
					        	profiles.put(Integer.toString(k), getProfileLevelString(types[j], profileLevel[k]));
					        }
				        	codec.put("PROFILES", profiles);
			        	}
		            } catch (final Exception e) {
			        	codec.put("PROFILES", e.getMessage());
		            }
                }
            }
            if (isvideo)
            	result.put(codecInfo.getName(), codec);
        }
		return result;
	}

	/**
	 * 音声コーデックの情報をJSONObjectとして取得する
	 * @return
	 * @throws JSONException
	 */
	private static JSONObject getAudio() throws JSONException {
		final JSONObject result = new JSONObject();
    	// コーデックの一覧を取得
        final int numCodecs = getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = getCodecInfoAt(i);
        	final JSONObject codec = new JSONObject();
            final String[] types = codecInfo.getSupportedTypes();
            final int n = types.length;
            boolean isaudio = false;
            for (int j = 0; j < n; j++) {
                if (types[j].startsWith("audio/")) {
                	isaudio = true;
                	codec.put(Integer.toString(j), types[j]);
                }
            }
            if (isaudio)
            	result.put(codecInfo.getName(), codec);
        }
		return result;
	}

    /**
     * 指定したcolorFormatを表す文字列を取得する
     * @param colorFormat
     * @return
     */
    public static String getColorFormatName(final int colorFormat) {
    	switch (colorFormat) {
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format12bitRGB444:
    		return "COLOR_Format12bitRGB444";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB1555:
    		return "COLOR_Format16bitARGB1555";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB4444:
    		return "COLOR_Format16bitARGB4444";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitBGR565:
    		return "COLOR_Format16bitBGR565";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565:
    		return "COLOR_Format16bitRGB565";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format18BitBGR666:
    		return "COLOR_Format18BitBGR666";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format18bitARGB1665:
    		return "COLOR_Format18bitARGB1665";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format18bitRGB666:
    		return "COLOR_Format18bitRGB666";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format19bitARGB1666:
    		return "COLOR_Format19bitARGB1666";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format24BitABGR6666:
    		return "COLOR_Format24BitABGR6666";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format24BitARGB6666:
    		return "COLOR_Format24BitARGB6666";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format24bitARGB1887:
    		return "COLOR_Format24bitARGB1887";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format24bitBGR888:
    		return "COLOR_Format24bitBGR888";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format24bitRGB888:
    		return "COLOR_Format24bitRGB888";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format25bitARGB1888:
    		return "COLOR_Format25bitARGB1888";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888:
    		return "COLOR_Format32bitARGB8888";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitBGRA8888:
    		return "COLOR_Format32bitBGRA8888";
    	case MediaCodecInfo.CodecCapabilities.COLOR_Format8bitRGB332:
    		return "COLOR_Format8bitRGB332";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatCbYCrY:
    		return "COLOR_FormatCbYCrY";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatCrYCbY:
    		return "COLOR_FormatCrYCbY";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL16:
    		return "COLOR_FormatL16";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL2:
    		return "COLOR_FormatL2";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL24:
    		return "COLOR_FormatL24";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL32:
    		return "COLOR_FormatL32";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL4:
    		return "COLOR_FormatL4";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatL8:
    		return "COLOR_FormatL8";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatMonochrome:
    		return "COLOR_FormatMonochrome";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer10bit:
    		return "COLOR_FormatRawBayer10bit";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bit:
    		return "COLOR_FormatRawBayer8bit";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bitcompressed:
    		return "COLOR_FormatRawBayer8bitcompressed";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface:	// = OMX_COLOR_FormatAndroidOpaque(0x7F000789)
    		return "COLOR_FormatSurface_COLOR_FormatAndroidOpaque";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr:
    		return "COLOR_FormatYCbYCr";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb:
    		return "COLOR_FormatYCrYCb";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411PackedPlanar:
    		return "COLOR_FormatYUV411PackedPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411Planar:
    		return "COLOR_FormatYUV411Planar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
    		return "COLOR_FormatYUV420PackedPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
    		return "COLOR_FormatYUV420PackedSemiPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
    		return "COLOR_FormatYUV420Planar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
    		return "COLOR_FormatYUV420SemiPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedPlanar:
    		return "COLOR_FormatYUV422PackedPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar:
    		return "COLOR_FormatYUV422PackedSemiPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar:
    		return "COLOR_FormatYUV422Planar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar:
    		return "COLOR_FormatYUV422SemiPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Interleaved:
    		return "COLOR_FormatYUV444Interleaved";
    	case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:	// = OMX_QCOM_COLOR_FormatYVU420SemiPlanar(0x7FA30C00)
    		return "COLOR_QCOM_FormatYUV420SemiPlanar";
    	case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar: // = OMX_TI_COLOR_FormatYUV420PackedSemiPlanar(0x7F000100)
    		return "COLOR_TI_FormatYUV420PackedSemiPlanar";
    	case 0x6F000000:
    		return "OMX_COLOR_FormatKhronosExtensions";
    	case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible: // = 0x7F420888;
    		return "COLOR_FormatYUV420Flexible";
    	case 0x7FA30C03:
    		return "OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka";
    	case 0x7FC00002:
    		return "OMX_SEC_COLOR_FormatNV12Tiled";
    	case 0x7FA30C04:
    		return "OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m";
    	default:
    		return String.format(Locale.getDefault(), "COLOR_Format_Unknown(%d)", colorFormat);
    	}
    }

	/**
	 * プロファイルレベルを文字列化する
	 * @param mimeType
	 * @param profileLevel
	 * @return
	 */
    public static String getProfileLevelString(final String mimeType, final MediaCodecInfo.CodecProfileLevel profileLevel) {
    	String result;
    	if (!TextUtils.isEmpty(mimeType)) {
	    	if (mimeType.equalsIgnoreCase("video/avc")) {
		    	switch (profileLevel.profile) {
		        // from OMX_VIDEO_AVCPROFILETYPE
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline:	// 0x01;
		    		result = "AVCProfileBaseline"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileMain:		// 0x02;
		    		result = "AVCProfileMain"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileExtended:	// 0x04;
		    		result = "AVCProfileExtended"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh:		// 0x08;
		    		result = "AVCProfileHigh"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10:		// 0x10;
		    		result = "AVCProfileHigh10"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422:	// 0x20;
		    		result = "AVCProfileHigh422"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444:	// 0x40;
		    		result = "AVCProfileHigh444"; break;
		    	default:
		    		result = "unknown profile " + profileLevel.profile; break;
		    	}
	    		switch (profileLevel.level) {
	            // from OMX_VIDEO_AVCLEVELTYPE
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel1:		// 0x01;
		    		result = result + ".AVCLevel1"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel1b:		// 0x02;
		    		result = result + ".AVCLevel1b"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel11:		// 0x04;
		    		result = result + ".AVCLevel11"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel12:		// 0x08;
		    		result = result + ".AVCLevel12"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel13:		// 0x10;
		    		result = result + ".AVCLevel13"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel2:		// 0x20;
		    		result = result + ".AVCLevel2"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel21:		// 0x40;
		    		result = result + ".AVCLevel21"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel22:		// 0x80;
		    		result = result + ".AVCLevel22"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel3:		// 0x100;
		    		result = result + ".AVCLevel3"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel31:		// 0x200;
		    		result = result + ".AVCLevel31"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel32:		// 0x400;
		    		result = result + ".AVCLevel32"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel4:		// 0x800;
		    		result = result + ".AVCLevel4"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel41:		// 0x1000;
		    		result = result + ".AVCLevel41"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel42:		// 0x2000;
		    		result = result + ".AVCLevel42"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel5:		// 0x4000;
		    		result = result + ".AVCLevel5"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AVCLevel51:		// 0x8000;
		    		result = result + ".AVCLevel51"; break;
		    	default:
		    		result = result + ".unknown level " + profileLevel.level; break;
	    		}
	    	} else if (mimeType.equalsIgnoreCase("video/h263")) {
		    	switch (profileLevel.profile) {
		    	// from OMX_VIDEO_H263PROFILETYPE
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileBaseline:				// 0x01;
		    		result = "H263ProfileBaseline"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileH320Coding:			// 0x02;
		    		result = "H263ProfileH320Coding"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileBackwardCompatible:	// 0x04;
		    		result = "H263ProfileBackwardCompatible"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileISWV2:					// 0x08;
		    		result = "H263ProfileISWV2"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileISWV3:					// 0x10;
		    		result = "H263ProfileISWV3"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileHighCompression:		// 0x20;
		    		result = "H263ProfileHighCompression"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileInternet:				// 0x40;
		    		result = "H263ProfileInternet"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileInterlace:				// 0x80;
		    		result = "H263ProfileInterlace"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263ProfileHighLatency:			// 0x100;
		    		result = "H263ProfileHighLatency"; break;
		    	default:
		    		result = "unknown profile " + profileLevel.profile; break;
		    	}
	    		switch (profileLevel.level) {
	            // from OMX_VIDEO_H263LEVELTYPE
		    	case MediaCodecInfo.CodecProfileLevel.H263Level10:					// 0x01;
		    		result = result + ".H263Level10"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level20:					// 0x02;
		    		result = result + ".H263Level20"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level30:					// 0x04;
		    		result = result + ".H263Level30"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level40:					// 0x08;
		    		result = result + ".H263Level40"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level45:					// 0x10;
		    		result = result + ".H263Level45"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level50:					// 0x20;
		    		result = result + ".H263Level50"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level60:					// 0x40;
		    		result = result + ".H263Level60"; break;
		    	case MediaCodecInfo.CodecProfileLevel.H263Level70:					// 0x80;
		    		result = result + ".H263Level70"; break;
		    	default:
		    		result = result + ".unknown level " + profileLevel.level; break;
	    		}
	    	} else if (mimeType.equalsIgnoreCase("video/mpeg4")) {
		    	switch (profileLevel.profile) {
	            // from OMX_VIDEO_MPEG4PROFILETYPE
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimple:			// 0x01;
		    		result = "MPEG4ProfileSimple"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleScalable:	// 0x02;
		    		result = "MPEG4ProfileSimpleScalable"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileCore:				// 0x04;
		    		result = "MPEG4ProfileCore"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileMain:				// 0x08;
		    		result = "MPEG4ProfileMain"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileNbit:				// 0x10;
		    		result = "MPEG4ProfileNbit"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileScalableTexture:	// 0x20;
		    		result = "MPEG4ProfileScalableTexture"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleFace:		// 0x40;
		    		result = "MPEG4ProfileSimpleFace"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleFBA:		// 0x80;
		    		result = "MPEG4ProfileSimpleFBA"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileBasicAnimated:	// 0x100;
		    		result = "MPEG4ProfileBasicAnimated"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileHybrid:			// 0x200;
		    		result = "MPEG4ProfileHybrid"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedRealTime:	// 0x400;
		    		result = "MPEG4ProfileAdvancedRealTime"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileCoreScalable:		// 0x800;
		    		result = "MPEG4ProfileCoreScalable"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedCoding:	// 0x1000;
		    		result = "MPEG4ProfileAdvancedCoding"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedCore:		// 0x2000;
		    		result = "MPEG4ProfileAdvancedCore"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedScalable:	// 0x4000;
		    		result = "MPEG4ProfileAdvancedScalable"; break;
		    	case MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedSimple:	// 0x8000;
		    		result = "MPEG4ProfileAdvancedSimple"; break;
		    	default:
		    		result = "unknown profile " + profileLevel.profile; break;
		    	}
	    		switch (profileLevel.level) {
	            // from OMX_VIDEO_MPEG4LEVELTYPE
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level0:			// 0x01;
	        		result = result + ".MPEG4Level0"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level0b:			// 0x02;
	        		result = result + ".MPEG4Level0b"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level1:			// 0x04;
	        		result = result + ".MPEG4Level1"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level2:			// 0x08;
	        		result = result + ".MPEG4Level2"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level3:			// 0x10;
	        		result = result + ".MPEG4Level3"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level4:			// 0x20;
	        		result = result + ".MPEG4Level4"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level4a:			// 0x40;
	        		result = result + ".MPEG4Level4a"; break;
	        	case MediaCodecInfo.CodecProfileLevel.MPEG4Level5:			// 0x80;
	        		result = result + ".MPEG4Level5"; break;
		    	default:
		    		result = result + ".unknown level " + profileLevel.level; break;
	    		}
	    	} else if (mimeType.equalsIgnoreCase("ausio/aac")) {
	            // from OMX_AUDIO_AACPROFILETYPE
		    	switch (profileLevel.level) {
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectMain:		// 1;
		    		result = "AACObjectMain"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectLC:			// 2;
		    		result = "AACObjectLC"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectSSR:			// 3;
		    		result = "AACObjectSSR"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectLTP:			// 4;
		    		result = "AACObjectLTP"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectHE:			// 5;
		    		result = "AACObjectHE"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectScalable:	// 6;
		    		result = "AACObjectScalable"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectERLC:		// 17;
		    		result = "AACObjectERLC"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectLD:			// 23;
		    		result = "AACObjectLD"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS:		// 29;
		    		result = "AACObjectHE_PS"; break;
		    	case MediaCodecInfo.CodecProfileLevel.AACObjectELD:			// 39;
		    		result = "AACObjectELD"; break;
				case MediaCodecInfo.CodecProfileLevel.AACObjectXHE:			// 42, xHE-AAC (includes USAC)
					result = "AACObjectXHE"; break;
		    	default:
		    		result = "profile:unknown " + profileLevel.profile; break;
		    	}
	    	} else if (mimeType.equalsIgnoreCase("video/vp8")) {
		    	switch (profileLevel.profile) {
	            // from OMX_VIDEO_VP8PROFILETYPE
		    	case MediaCodecInfo.CodecProfileLevel.VP8ProfileMain:		// 0x01;
		    		result = "VP8ProfileMain"; break;
		    	default:
		    		result = "unknown profile " + profileLevel.profile; break;
		    	}
				switch (profileLevel.level) {
	            // from OMX_VIDEO_VP8LEVELTYPE
		    	case MediaCodecInfo.CodecProfileLevel.VP8Level_Version0:	// 0x01;
		    		result = result + ".VP8Level_Version0"; break;
		    	case MediaCodecInfo.CodecProfileLevel.VP8Level_Version1:	// 0x02;
		    		result = result + ".VP8Level_Version1"; break;
		    	case MediaCodecInfo.CodecProfileLevel.VP8Level_Version2:	// 0x04;
		    		result = result + ".VP8Level_Version2"; break;
		    	case MediaCodecInfo.CodecProfileLevel.VP8Level_Version3:	// 0x08;
		    		result = result + ".VP8Level_Version3"; break;
		    	default:
		    		result = result + ".unknown level" + profileLevel.level; break;
		    	}
			} else if (mimeType.equalsIgnoreCase("video/vp9")) {
				switch (profileLevel.profile) {
				case MediaCodecInfo.CodecProfileLevel.VP9Profile0:			// 0x01, VP9 Profile 0 4:2:0 8-bit
					result = "VP9Profile0"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile1:			// 0x02, VP9 Profile 1 4:2:2 8-bit
					result = "VP9Profile1"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile2:			// 0x04, VP9 Profile 2 4:2:0 10-bit
					result = "VP9Profile2"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile3:			// 0x08, VP9 Profile 3 4:2:2 10-bit
					result = "VP9Profile3"; break;
    			// HDR profiles also support passing HDR metadata
				case MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR:		// 0x1000, VP9 Profile 2 4:2:0 10-bit HDR
					result = "VP9Profile2HDR"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR:		// 0x2000, VP9 Profile 3 4:2:2 10-bit HDR
					result = "VP9Profile3HDR"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus:	// 0x4000, VP9 Profile 2 4:2:0 10-bit HDR10Plus
					result = "VP9Profile2HDR10Plus"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR10Plus:	// 0x8000, VP9 Profile 3 4:2:2 10-bit HDR10Plus
					result = "VP9Profile3HDR10Plus"; break;
				default:
					result = "unknown profile " + profileLevel.profile;
					break;
				}
				switch (profileLevel.level) {
				case MediaCodecInfo.CodecProfileLevel.VP9Level1:			// 0x1
					result = result + ".VP9Level1"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level11:			// 0x2
					result = result + ".VP9Level11"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level2:			// 0x4
					result = result + ".VP9Level2"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level21:			// 0x8
					result = result + ".VP9Level21"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level3:			// 0x10
					result = result + ".VP9Level3"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level31:			// 0x20
					result = result + ".VP9Level31"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level4:			// 0x40
					result = result + ".VP9Level4"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level41:			// 0x80
					result = result + ".VP9Level41"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level5:			// 0x100
					result = result + ".VP9Level5"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level51:			// 0x200
					result = result + ".VP9Level51"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level52:			// 0x400
					result = result + ".VP9Level52"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level6:			// 0x800
					result = result + ".VP9Level6"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level61:			// 0x1000
					result = result + ".VP9Level61"; break;
				case MediaCodecInfo.CodecProfileLevel.VP9Level62:			// 0x2000
					result = result + ".VP9Level62"; break;
				default:
					result = result + ".unknown level" + profileLevel.level;
					break;
				}
			} else if (mimeType.equalsIgnoreCase("video/hevc")) {
				switch (profileLevel.profile) {
				case MediaCodecInfo.CodecProfileLevel.HEVCProfileMain:		// 0x01
					result = "HEVCProfileMain"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10:	// 0x02
					result = "HEVCProfileMain10"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill:	// 0x04
					result = "HEVCProfileMainStill"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10:	// 0x1000
					result = "HEVCProfileMain10HDR10"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus:	// 0x2000
					result = "HEVCProfileMain10HDR10Plus"; break;
				default:
					result = "unknown profile " + profileLevel.profile;
					break;
				}
				switch (profileLevel.level) {
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1:	// 0x1
					result = result + ".HEVCMainTierLevel1"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1:	// 0x2
					result = result + ".HEVCHighTierLevel1"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2:	// 0x4
					result = result + ".HEVCMainTierLevel2"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2:	// 0x8
					result = result + ".HEVCHighTierLevel2"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21:	// 0x10
					result = result + ".HEVCMainTierLevel21"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21:	// 0x20
					result = result + ".HEVCHighTierLevel21"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3:	// 0x40
					result = result + ".HEVCMainTierLevel3"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3:	// 0x80
					result = result + ".HEVCHighTierLevel3"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31:	// 0x100
					result = result + ".HEVCMainTierLevel31"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31:	// 0x200
					result = result + ".HEVCHighTierLevel31"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4:	// 0x400
					result = result + ".HEVCMainTierLevel4"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4:	// 0x800
					result = result + ".HEVCHighTierLevel4"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41:	// 0x1000
					result = result + ".HEVCMainTierLevel41"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41:	// 0x2000
					result = result + ".HEVCHighTierLevel41"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5:	// 0x4000
					result = result + ".HEVCMainTierLevel5"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5:	// 0x8000
					result = result + ".HEVCHighTierLevel5"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51:	// 0x10000
					result = result + ".HEVCMainTierLevel51"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51:	// 0x20000
					result = result + ".HEVCHighTierLevel51"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52:	// 0x40000
					result = result + ".HEVCMainTierLevel52"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52:	// 0x80000
					result = result + ".HEVCHighTierLevel52"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6:	// 0x100000
					result = result + ".HEVCMainTierLevel6"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6:	// 0x200000
					result = result + ".HEVCHighTierLevel6"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61:	// 0x400000
					result = result + ".HEVCMainTierLevel61"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61:	// 0x800000
					result = result + ".HEVCHighTierLevel61"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62:	// 0x1000000
					result = result + ".HEVCMainTierLevel62"; break;
				case MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62:	// 0x2000000
					result = result + ".HEVCHighTierLevel62"; break;
				default:
					result = result + ".unknown level" + profileLevel.level;
					break;
				}
	    	} else {
	    		result = "unknown profile " + profileLevel.profile;
	    	}
    	} else {
    		result = "mime type is null";
    	}

    	return result;
    }

//--------------------------------------------------------------------------------
    // 静的にキャッシュするようにした
    @NonNull
    private static final List<MediaCodecInfo> sCodecList = new ArrayList<MediaCodecInfo>();

	/**
	 * コーデックの情報を更新する
	 */
	private static void updateCodecs() {
    	if (sCodecList.size() == 0) {
	    	// コーデックの一覧を取得
	        final int n = MediaCodecList.getCodecCount();
	        for (int i = 0; i < n; i++) {
	        	sCodecList.add(MediaCodecList.getCodecInfoAt(i));
	        }
    	}
    }

	/**
	 * コーデックの数を取得する
	 * @return
	 */
    public static int getCodecCount() {
    	updateCodecs();
    	return sCodecList.size();
    }

	/**
	 * コーデック情報一覧を取得する
	 * @return
	 */
    public static List<MediaCodecInfo> getCodecs() {
    	updateCodecs();
    	return sCodecList;
    }

	/**
	 * 指定したインデックスに対応するコーデック情報を取得する
	 * @param ix
	 * @return
	 */
    public static MediaCodecInfo getCodecInfoAt(final int ix) {
    	updateCodecs();
    	return sCodecList.get(ix);
    }

	/**
	 * getCapabilitiesForTypeがすごく遅い機種があるので静的にキャッシュする
	 */
	private static final HashMap<String, HashMap<MediaCodecInfo, MediaCodecInfo.CodecCapabilities>>
		sCapabilities = new HashMap<String, HashMap<MediaCodecInfo, MediaCodecInfo.CodecCapabilities>>();

	/**
	 * CodecCapabilitiesを取得
	 * @param codecInfo
	 * @param mimeType
	 * @return
	 */
    public static MediaCodecInfo.CodecCapabilities getCodecCapabilities(final MediaCodecInfo codecInfo, final String mimeType) {
		HashMap<MediaCodecInfo, MediaCodecInfo.CodecCapabilities> caps = sCapabilities.get(mimeType);
		if (caps == null) {
			caps = new HashMap<MediaCodecInfo, MediaCodecInfo.CodecCapabilities>();
			sCapabilities.put(mimeType, caps);
		}
		MediaCodecInfo.CodecCapabilities capabilities = caps.get(codecInfo);
		if (capabilities == null) {
	    	// XXX 通常の優先度ではSC-06DでMediaCodecInfo#getCapabilitiesForTypeが返ってこないので一時的に昇格
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			try {
				capabilities = codecInfo.getCapabilitiesForType(mimeType);
				caps.put(codecInfo, capabilities);
			} finally {
				// 元の優先度に戻す
				Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
			}
		}
		return capabilities;
	}

//--------------------------------------------------------------------------------
	/**
	 * 指定したMIMEで使用可能がcodecの一覧の中から先頭のものを取得する
	 * もし使用可能なのがなければnullを返す
	 * @param mimeType
	 */
	@Nullable
	public static MediaCodecInfo selectVideoEncoder(final String mimeType) {
		// コーデックの一覧を取得
		final int numCodecs = getCodecCount();
		for (int i = 0; i < numCodecs; i++) {
			final MediaCodecInfo codecInfo = getCodecInfoAt(i);

			if (!codecInfo.isEncoder()) {	// エンコーダーでない(=デコーダー)はスキップする
				continue;
			}
			// エンコーダーの一覧からMIMEが一致してカラーフォーマットが使用可能なものを選択する
/*			// こっちの方法で選択すると
			// W/OMXCodec(20608): Failed to set standard component role 'video_encoder.avc'.
			// って表示されて、OMX.Nvidia.mp4.encoder
			// が選択される
			final MediaCodecInfo.CodecCapabilities caps;
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			try {
				caps = getCodecCapabilities(codecInfo, mimeType);
			} finally {
				Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
			}
			final int[] colorFormats = caps.colorFormats;
			final int n = colorFormats != null ? colorFormats.length : 0;
			int colorFormat;
			for (int j = 0; j < n; j++) {
				colorFormat = colorFormats[j];
				if (isRecognizedVideoFormat(colorFormat)) {
					mColorFormat = colorFormat;
					return codecInfo;
				}
			} */
// こっちで選択すると、xxx.h264.encoderが選択される
			final String[] types = codecInfo.getSupportedTypes();
			final int n = types.length;
			int format;
			for (int j = 0; j < n; j++) {
				if (types[j].equalsIgnoreCase(mimeType)) {
//                	if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
					format = selectColorFormat(codecInfo, mimeType);
					if (format > 0) {
						return codecInfo;
					}
				}
			}
		}
		return null;
	}

	/**
	 * 指定したmimeに対応するビデオコーデックのエンコーダー一覧を取得する
	 * @param mimeType
	 * @return
	 */
	@NonNull
	public static List<MediaCodecInfo> getVideoEncoderInfos(final String mimeType) {
		final List<MediaCodecInfo> result = new ArrayList<>();
		// コーデックの一覧を取得
		final int numCodecs = getCodecCount();
		for (int i = 0; i < numCodecs; i++) {
			final MediaCodecInfo codecInfo = getCodecInfoAt(i);

			if (!codecInfo.isEncoder()) {	// エンコーダーでない(=デコーダー)はスキップする
				continue;
			}
			// エンコーダーの一覧からMIMEが一致してカラーフォーマットが使用可能なものを選択する
/*			// こっちの方法で選択すると
			// W/OMXCodec(20608): Failed to set standard component role 'video_encoder.avc'.
			// って表示されて、OMX.Nvidia.mp4.encoder
			// が選択される
			final MediaCodecInfo.CodecCapabilities caps;
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			try {
				caps = getCodecCapabilities(codecInfo, mimeType);
			} finally {
				Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
			}
			final int[] colorFormats = caps.colorFormats;
			final int n = colorFormats != null ? colorFormats.length : 0;
			int colorFormat;
			for (int j = 0; j < n; j++) {
				colorFormat = colorFormats[j];
				if (isRecognizedVideoFormat(colorFormat)) {
					result.add(codecInfo);
				}
			} */
// こっちで選択すると、xxx.h264.encoderが選択される
			final String[] types = codecInfo.getSupportedTypes();
			final int n = types.length;
			int format;
			for (int j = 0; j < n; j++) {
				if (types[j].equalsIgnoreCase(mimeType)) {
//                	if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
					format = selectColorFormat(codecInfo, mimeType);
					if (format > 0) {
						result.add(codecInfo);
					}
				}
			}
		}
		return result;
	}

	/**
	 * 使用可能なカラーフォーマットを設定
	 */
	public static int[] recognizedFormats;
	static {
		recognizedFormats = new int[] {
			MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
			MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
			MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
		};
	}

	/**
	 * 指定したカラーフォーマットをこのEncoderで使用可能かどうかを返す
	 * @param colorFormat
	 * @return
	 */
    public static boolean isRecognizedVideoFormat(final int colorFormat) {
    	final int n = recognizedFormats != null ? recognizedFormats.length : 0;
    	for (int i = 0; i < n; i++) {
    		if (recognizedFormats[i] == colorFormat) {
    			return true;
    		}
    	}
    	return false;
    }

	/**
	 * codecがサポートしているカラーフォーマットの中から最初に使用可能なものを選択して返す
	 * 使用可能なカラーフォーマットはrecognizedFormatsに設定する(Encoderで設定しているのはYUV420PlanarかYUV420SemiPlanar)
	 * 使用可能なものが無ければ0を返す
	 */
	public static int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
		int result = 0;
		final MediaCodecInfo.CodecCapabilities capabilities = getCodecCapabilities(codecInfo, mimeType);
		final int[] colorFormats = capabilities.colorFormats;
		final int n = colorFormats.length;
		int colorFormat;
		for (int i = 0; i < n; i++) {
			colorFormat = colorFormats[i];
			if (isRecognizedVideoFormat(colorFormat)) {
				result = colorFormat;
				break;	// if (!DEBUG) break;
			}
		}
        return result;
    }

	/**
	 * エンコード用コーデックの一覧をログに出力する
	 */
	public static void dumpEncoders() {
		dumpEncoders(TAG);
	}

	/**
	 * エンコード用コーデックの一覧をログに出力する
	 * @param tag
	 */
	public static void dumpEncoders(@Nullable final String tag) {
		final String _tag = TextUtils.isEmpty(tag) ? TAG : tag;
    	// コーデックの一覧を取得
        final int numCodecs = getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = getCodecInfoAt(i);	// API >= 16

            if (!codecInfo.isEncoder()) {	// エンコーダーでない(デコーダー)はとばす
                continue;
            }
			// コーデックの一覧を出力する
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
            	Log.i(_tag, "codec:" + codecInfo.getName() + ",MIME:" + types[j]);
            	// カラーフォーマットを出力する
            	selectColorFormat(codecInfo, types[j]);
            }
        }
    }

	/**
	 * デコード用コーデックの一覧をログに出力する
	 */
	public static void dumpDecoders() {
		dumpDecoders(TAG);
	}

	/**
	 * デコード用コーデックの一覧をログに出力する
	 * @param tag
	 */
	public static void dumpDecoders(@Nullable final String tag) {
		final String _tag = TextUtils.isEmpty(tag) ? TAG : tag;
    	// コーデックの一覧を取得
        final int numCodecs = getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = getCodecInfoAt(i);	// API >= 16

            if (codecInfo.isEncoder()) {	// エンコーダーはとばす
                continue;
            }
            // コーデックの一覧を出力する
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
            	Log.i(_tag, "codec:" + codecInfo.getName() + ",MIME:" + types[j]);
            	// カラーフォーマットを出力する
            	selectColorFormat(codecInfo, types[j]);
            }
        }
    }

    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    public static boolean isSemiPlanarYUV(final int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

//================================================================================
	/**
	 * 指定したMIMEに一致する最初の音声エンコード用コーデックを選択する
	 * 対応するものがなければnullを返す
	 * @param mimeType
	 * @return
	 */
	@Nullable
	public static MediaCodecInfo selectAudioEncoder(final String mimeType) {
//    	if (DEBUG) Log.v(TAG, "selectAudioCodec:");

 		MediaCodecInfo result = null;
 		// コーデックの一覧を取得
		final int numCodecs = getCodecCount();
LOOP:	for (int i = 0; i < numCodecs; i++) {
     	final MediaCodecInfo codecInfo = getCodecInfoAt(i);
			if (!codecInfo.isEncoder()) {	// エンコーダーでない(=デコーダー)はスキップする
				continue;
			}
			final String[] types = codecInfo.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
//				if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
				if (types[j].equalsIgnoreCase(mimeType)) {
					result = codecInfo;
					break LOOP;
				}
			}
		}
		return result;
	}

	/**
	 * 指定したmimeに対応する音声コーデックのエンコーダー一覧を取得する
	 * @param mimeType
	 * @return
	 */
	@NonNull
	public static List<MediaCodecInfo> getAudioEncoderInfos(final String mimeType) {
		final List<MediaCodecInfo> result = new ArrayList<>();

		// コーデックの一覧を取得
		final int numCodecs = getCodecCount();
LOOP:	for (int i = 0; i < numCodecs; i++) {
			final MediaCodecInfo codecInfo = getCodecInfoAt(i);
			if (!codecInfo.isEncoder()) {	// エンコーダーでない(=デコーダー)はスキップする
				continue;
			}
			final String[] types = codecInfo.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
//				if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
				if (types[j].equalsIgnoreCase(mimeType)) {
					result.add(codecInfo);
				}
			}
		}
		return result;
	}

//--------------------------------------------------------------------------------

	/**
	 * プロファイル・レベルが低ければtrueを返す
	 * @param mimeType
	 * @param info
	 * @return
	 */
	public static boolean checkProfileLevel(final String mimeType,
		final MediaCodecInfo info) {

		if (info != null) {
			if (mimeType.equalsIgnoreCase("video/avc")) {
				final MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(info, mimeType);
				final MediaCodecInfo.CodecProfileLevel[] profileLevel = caps.profileLevels;
				for (int j = 0; j < profileLevel.length; j++) {
					if (profileLevel[j].level >= MediaCodecInfo.CodecProfileLevel.AVCLevel5)
						return false;
				}
			}
		}
		return true;
	}

	/**
	 * codec specific dataの先頭マーカー位置を検索
	 * BufferHelper.byteCompのシノニム
	 * @param array
	 * @param offset
	 * @return
	 */
	public static int findStartMarker(@NonNull final byte[] array, final int offset) {
		return BufferHelper.byteComp(array, offset, START_MARKER, START_MARKER.length);
	}

	/**
	 * 指定したMediaFormatをlogCatへダンプする
	 * @param format
	 */
	public static void dump(@NonNull final MediaFormat format) {
		dump(TAG, format);
	}

	/**
	 * 指定したMediaFormatをlogCatへダンプする
	 * @param tag
	 * @param format
	 */
	public static void dump(@Nullable final String tag, @NonNull final MediaFormat format) {
		final String _tag = TextUtils.isEmpty(tag) ? TAG : tag;
		Log.i(_tag, "format:" + format.toString());
		ByteBuffer csd0 = format.getByteBuffer("csd-0");
		if (csd0 != null) {
			csd0 = csd0.asReadOnlyBuffer();
			csd0.position(csd0.capacity()).flip();
			BufferHelper.dump(_tag, "csd0:", csd0, 0, 64);
		}
		ByteBuffer csd1 = format.getByteBuffer("csd-1");
		if (csd1 != null) {
			csd1 = csd1.asReadOnlyBuffer();
			csd1.position(csd1.capacity()).flip();
			BufferHelper.dump(_tag, "csd1:", csd1, 0, 64);
		}
	}

	/**
	 * 指定したMediaCodecInfoをlogCatへダンプする
	 * @param info
	 */
	public static void dump(@NonNull final MediaCodecInfo info) {
		dump(TAG, info);
	}

	/**
	 * 指定したMediaCodecInfoをlogCatへダンプする
	 * @param tag
	 * @param info
	 */
	public static void dump(@Nullable final String tag, @NonNull final MediaCodecInfo info) {
		final String _tag = TextUtils.isEmpty(tag) ? TAG : tag;
		final String[] supportedTypes = info.getSupportedTypes();
		Log.i(_tag, "name=" + info.getName());
		if (BuildCheck.isAPI29()) {
			Log.i(_tag, "canonicalName=" + info.getCanonicalName());
			Log.i(_tag, "isAlias=" + info.isAlias());
			Log.i(_tag, "isHardwareAccelerated=" + info.isHardwareAccelerated());
			Log.i(_tag, "isSoftwareOnly=" + info.isSoftwareOnly());
			Log.i(_tag, "isVendor=" + info.isVendor());
		}
		Log.i(_tag, "isEncoder=" + info.isEncoder());
		Log.i(_tag, "supportedTypes=" + Arrays.toString(supportedTypes));
		for (final String type: supportedTypes) {
			final MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
			final int[] colorFormats = caps.colorFormats;
			for (final int colorFormat: colorFormats) {
				Log.i(_tag, "type=" + type + ",colorFormat=" + getColorFormatName(colorFormat));
			}
			final MediaCodecInfo.CodecProfileLevel[] profileLevels = caps.profileLevels;
			for (final MediaCodecInfo.CodecProfileLevel profileLevel: profileLevels) {
				Log.i(_tag, "type=" + type + ",profileLevel=" + getProfileLevelString(type, profileLevel));
			}
		}
	}

	/**
	 * 実際に映像をエンコードしてMediaFormatを取得する
	 * 映像ソースはsurfaceから入力する
	 * @param mime
	 * @param width
	 * @param height
	 * @return
	 */
	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
	@NonNull
	public static MediaFormat testVideoMediaFormat(
		@NonNull final String mime, final int width, final int height) throws IOException {

		return testVideoMediaFormat(mime, width, height, new VideoConfig());
	}

	/**
	 * 実際に映像をエンコードしてMediaFormatを取得する
	 * 映像ソースはsurfaceから入力する
	 * @param mime 映像エンコーダーの種類
	 * @param width 映像幅
	 * @param height 映像高さ
	 * @param config エンコード設定
	 * @return
	 */
	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
	@NonNull
	public static MediaFormat testVideoMediaFormat(
		@NonNull final String mime, final int width, final int height,
		@Nullable final VideoConfig config) throws IOException {

		if (DEBUG) Log.v(TAG, String.format("testVideoMediaFormat:mime=%s,size(%dx%d)", mime, width, height));
		final VideoConfig _config = config != null ? config : new VideoConfig();
		final MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);

		// MediaCodecに適用するパラメータを設定する。誤った設定をするとMediaCodec#configureが
		// 復帰不可能な例外を生成する
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);    // API >= 18
		format.setInteger(MediaFormat.KEY_BIT_RATE, _config.getBitrate(width, height));
		format.setInteger(MediaFormat.KEY_FRAME_RATE, _config.captureFps());
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, _config.calcIFrameIntervals());
		if (DEBUG) dump(TAG, format);

		// 設定したフォーマットに従ってMediaCodecのエンコーダーを生成する
		// エンコーダーへの入力に使うSurfaceを取得する
		final AtomicReference<MediaFormat> result = new AtomicReference<>();
		final CountDownLatch latch = new CountDownLatch(1);
		if (DEBUG) Log.v(TAG, "testVideoMediaFormat:create encoder");
		final MediaCodec encoder = MediaCodec.createEncoderByType(mime);
		if (DEBUG) Log.v(TAG, "testVideoMediaFormat:configure encoder");
		encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		final Surface surface = encoder.createInputSurface();    // API >= 18
		if (DEBUG) Log.v(TAG, "testVideoMediaFormat:start encoder");
		encoder.start();
		if (DEBUG) Log.v(TAG, "testVideoMediaFormat:create and start VideoReaper");
		final MediaReaper.VideoReaper reaper = new MediaReaper.VideoReaper(
			encoder, new MediaReaper.ReaperListener() {
			@Override
			public void writeSampleData(@NonNull final MediaReaper reaper, @NonNull final ByteBuffer byteBuf, @NonNull final MediaCodec.BufferInfo bufferInfo) {
				if (DEBUG) Log.v(TAG, "writeSampleData:");
			}

			@Override
			public void onOutputFormatChanged(@NonNull final MediaReaper reaper, @NonNull final MediaFormat format) {
				if (DEBUG) Log.v(TAG, "onOutputFormatChanged:");
				result.set(format);
				latch.countDown();
			}

			@Override
			public void onStop(@NonNull final MediaReaper reaper) {
				if (DEBUG) Log.v(TAG, "onStop:");
			}

			@Override
			public void onError(@NonNull final MediaReaper reaper, final Throwable t) {
				Log.w(TAG, t);
			}
		},
		width, height);
		try {
			for (int i = 0; i < 10; i++) {
				final Canvas canvas = surface.lockCanvas(null);
				try {
					canvas.drawColor(0xffff0000);
				} finally {
					surface.unlockCanvasAndPost(canvas);
				}
				reaper.frameAvailableSoon();
				try {
					if (DEBUG) Log.v(TAG, "testVideoMediaFormat:wait MediaFormat");
					if (latch.await(15, TimeUnit.MILLISECONDS)) {
						break;
					}
				} catch (final InterruptedException e) {
					break;
				}
			}
		} finally {
			encoder.stop();
			encoder.release();
			reaper.release();
		}
		if (DEBUG) Log.i(TAG, "testVideoMediaFormat:result=" + result.get());
		return result.get();
	}

	/**
	 * 実際にダミー音声データをエンコードしてMediaFormatを取得する
	 * @param mime 音声エンコーダーの種類
	 * @param sampleRate サンプリングレート AbstractAudioEncoder.DEFAULT_SAMPLE_RATE = 44100
	 * @param channelCount チャネル数 1または2
	 * @param bitrate ビットレート AbstractAudioEncoder.DEFAULT_BIT_RATE = 64000
	 * @return
	 * @throws IOException
	 */
	@NonNull
	public static MediaFormat testAudioMediaFormat(
		@NonNull final String mime,
		final int sampleRate, final int channelCount,
		final int bitrate) throws IOException {

		return testAudioMediaFormat(mime,
			sampleRate, channelCount, bitrate,
			AudioRecordCompat.SAMPLES_PER_FRAME, AudioRecordCompat.FRAMES_PER_BUFFER);
	}

	/**
	 * 実際にダミー音声データをエンコードしてMediaFormatを取得する
	 * @param mime 音声エンコーダーの種類
	 * @param sampleRate サンプリングレート AbstractAudioEncoder.DEFAULT_SAMPLE_RATE = 44100
	 * @param channelCount チャネル数 1または2
	 * @param bitrate ビットレート AbstractAudioEncoder.DEFAULT_BIT_RATE = 64000
	 * @param samplesPerFrame AbstractAudioEncoder.SAMPLES_PER_FRAME
	 * @param framesPerBuffer AbstractAudioEncoder.FRAMES_PER_BUFFER
	 * @return
	 * @throws IOException
	 */
	@NonNull
	public static MediaFormat testAudioMediaFormat(
		@NonNull final String mime,
		final int sampleRate, final int channelCount,
		final int bitrate,
		final int samplesPerFrame, final int framesPerBuffer) throws IOException {

		final MediaFormat format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioRecordCompat.getAudioChannel(channelCount));
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
		if (DEBUG) dump(TAG, format);

		// 設定したフォーマットに従ってMediaCodecのエンコーダーを生成する
		if (DEBUG) Log.v(TAG, "testAudioMediaFormat:create encoder");
		final MediaCodec encoder = MediaCodec.createEncoderByType(mime);
		if (DEBUG) Log.v(TAG, "testAudioMediaFormat:configure encoder");
		encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

		if (DEBUG) Log.v(TAG, "testAudioMediaFormat:create and start AudioReaper");
		final AtomicReference<MediaFormat> result = new AtomicReference<>();
		final CountDownLatch latch = new CountDownLatch(1);
		final MediaReaper.AudioReaper reaper = new MediaReaper.AudioReaper(
			encoder, new MediaReaper.ReaperListener() {
			@Override
			public void writeSampleData(@NonNull final MediaReaper reaper, @NonNull final ByteBuffer byteBuf, @NonNull final MediaCodec.BufferInfo bufferInfo) {
				if (DEBUG) Log.v(TAG, "writeSampleData:");
			}

			@Override
			public void onOutputFormatChanged(@NonNull final MediaReaper reaper, @NonNull final MediaFormat format) {
				if (DEBUG) Log.v(TAG, "onOutputFormatChanged:");
				result.set(format);
				latch.countDown();
			}

			@Override
			public void onStop(@NonNull final MediaReaper reaper) {
				if (DEBUG) Log.v(TAG, "onStop:");
			}

			@Override
			public void onError(@NonNull final MediaReaper reaper, final Throwable t) {
				Log.w(TAG, t);
			}
		}, sampleRate, channelCount);

		if (DEBUG) Log.v(TAG, "testAudioMediaFormat:start encoder");
		encoder.start();

		final int bufferSize = AudioRecordCompat.getAudioBufferSize(
			channelCount, AudioRecordCompat.AUDIO_FORMAT,
			sampleRate, samplesPerFrame, framesPerBuffer);
		if (DEBUG) Log.v(TAG, "testAudioMediaFormat:buffer size=" + bufferSize);
		try {
			final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
			final long startTimeUs = System.nanoTime() / 1000L;
			int numFrames = 0;
			while (numFrames < 10) {
				final ByteBuffer[] inputBuffers = encoder.getInputBuffers();
				final int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
				if (inputBufferIndex >= 0) {
					final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
					final int sz = Math.min(bufferSize, inputBuffer.capacity());
					buffer.position(sz);
					buffer.flip();
					inputBuffer.clear();
					inputBuffer.put(buffer);
					if (DEBUG) Log.v(TAG, "testAudioMediaFormat:queueInputBuffer," + numFrames);
					final long ptsUs = startTimeUs + numFrames * 40000L;
						if (numFrames > 2 && (latch.getCount() == 0)) {
							if (DEBUG) Log.v(TAG, "testAudioMediaFormat:queue EOS");
							encoder.queueInputBuffer(inputBufferIndex, 0, 0,
								ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							reaper.frameAvailableSoon();
							break;
						} else {
							encoder.queueInputBuffer(inputBufferIndex, 0, sz,
								ptsUs, 0);
						}
					numFrames++;
				}
				reaper.frameAvailableSoon();
			}
			try {
				if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
					throw new IOException("Failed to get output format");
				}
			} catch (final InterruptedException e) {
				// ignore
			}
		} finally {
			encoder.stop();
			encoder.release();
			reaper.release();
		}
		if (DEBUG) Log.i(TAG, "testAudioMediaFormat:result=" + result.get());
		return result.get();
	}

	/**
	 * MediaFormatのシリアライズ用, Gsonの方が良かった？
	 * @param format
	 * @return
	 */
	@SuppressLint("InlinedApi")
	public static String asString(@NonNull final MediaFormat format) {
		final JSONObject map = new JSONObject();
		try {
			if (format.containsKey(MediaFormat.KEY_MIME))
				map.put(MediaFormat.KEY_MIME,
					format.getString(MediaFormat.KEY_MIME));
			if (format.containsKey(MediaFormat.KEY_WIDTH))
				map.put(MediaFormat.KEY_WIDTH,
					format.getInteger(MediaFormat.KEY_WIDTH));
			if (format.containsKey(MediaFormat.KEY_HEIGHT))
				map.put(MediaFormat.KEY_HEIGHT,
					format.getInteger(MediaFormat.KEY_HEIGHT));
			if (format.containsKey(MediaFormat.KEY_BIT_RATE))
				map.put(MediaFormat.KEY_BIT_RATE,
					format.getInteger(MediaFormat.KEY_BIT_RATE));
			if (format.containsKey(MediaFormat.KEY_COLOR_FORMAT))
				map.put(MediaFormat.KEY_COLOR_FORMAT,
					format.getInteger(MediaFormat.KEY_COLOR_FORMAT));
			if (format.containsKey(MediaFormat.KEY_FRAME_RATE))
				map.put(MediaFormat.KEY_FRAME_RATE,
					format.getInteger(MediaFormat.KEY_FRAME_RATE));
			if (format.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL))
				map.put(MediaFormat.KEY_I_FRAME_INTERVAL,
					format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
			if (format.containsKey(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER))
				map.put(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
					format.getLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER));
			if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
				map.put(MediaFormat.KEY_MAX_INPUT_SIZE,
					format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
			if (format.containsKey(MediaFormat.KEY_DURATION))
				map.put(MediaFormat.KEY_DURATION,
					format.getInteger(MediaFormat.KEY_DURATION));
			if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
				map.put(MediaFormat.KEY_CHANNEL_COUNT,
					format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
			if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
				map.put(MediaFormat.KEY_SAMPLE_RATE,
					format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
			if (format.containsKey(MediaFormat.KEY_CHANNEL_MASK))
				map.put(MediaFormat.KEY_CHANNEL_MASK,
					format.getInteger(MediaFormat.KEY_CHANNEL_MASK));
			if (format.containsKey(MediaFormat.KEY_AAC_PROFILE))
				map.put(MediaFormat.KEY_AAC_PROFILE,
					format.getInteger(MediaFormat.KEY_AAC_PROFILE));
			if (format.containsKey(MediaFormat.KEY_AAC_SBR_MODE))
				map.put(MediaFormat.KEY_AAC_SBR_MODE,
					format.getInteger(MediaFormat.KEY_AAC_SBR_MODE));
			if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
				map.put(MediaFormat.KEY_MAX_INPUT_SIZE,
					format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
			if (format.containsKey(MediaFormat.KEY_IS_ADTS))
				map.put(MediaFormat.KEY_IS_ADTS,
					format.getInteger(MediaFormat.KEY_IS_ADTS));
			if (format.containsKey("what"))
				map.put("what", format.getInteger("what"));
			if (format.containsKey("csd-0"))
				map.put("csd-0", asString(format.getByteBuffer("csd-0")));
			if (format.containsKey("csd-1"))
				map.put("csd-1", asString(format.getByteBuffer("csd-1")));
			if (format.containsKey("csd-2"))
				map.put("csd-2", asString(format.getByteBuffer("csd-2")));
		} catch (final JSONException e) {
			Log.e(TAG, "writeFormat:", e);
		}

		return map.toString();
	}

	/**
	 * MediaFormatのデシリアライズ用, Gsonの方が良かった？
	 * @param format_str
	 * @return
	 */
	@SuppressLint("InlinedApi")
	public static MediaFormat asMediaFormat(@NonNull final String format_str) {
		MediaFormat format = new MediaFormat();
		try {
			final JSONObject map = new JSONObject(format_str);
			if (map.has(MediaFormat.KEY_MIME))
				format.setString(MediaFormat.KEY_MIME,
					(String)map.get(MediaFormat.KEY_MIME));
			if (map.has(MediaFormat.KEY_WIDTH))
				format.setInteger(MediaFormat.KEY_WIDTH,
					(Integer)map.get(MediaFormat.KEY_WIDTH));
			if (map.has(MediaFormat.KEY_HEIGHT))
				format.setInteger(MediaFormat.KEY_HEIGHT,
					(Integer)map.get(MediaFormat.KEY_HEIGHT));
			if (map.has(MediaFormat.KEY_BIT_RATE))
				format.setInteger(MediaFormat.KEY_BIT_RATE,
					(Integer)map.get(MediaFormat.KEY_BIT_RATE));
			if (map.has(MediaFormat.KEY_COLOR_FORMAT))
				format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					(Integer)map.get(MediaFormat.KEY_COLOR_FORMAT));
			if (map.has(MediaFormat.KEY_FRAME_RATE))
				format.setInteger(MediaFormat.KEY_FRAME_RATE,
					(Integer)map.get(MediaFormat.KEY_FRAME_RATE));
			if (map.has(MediaFormat.KEY_I_FRAME_INTERVAL))
				format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
					(Integer)map.get(MediaFormat.KEY_I_FRAME_INTERVAL));
			if (map.has(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER))
				format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
					(Long)map.get(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER));
			if (map.has(MediaFormat.KEY_MAX_INPUT_SIZE))
				format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,
					(Integer)map.get(MediaFormat.KEY_MAX_INPUT_SIZE));
			if (map.has(MediaFormat.KEY_DURATION))
				format.setInteger(MediaFormat.KEY_DURATION,
					(Integer)map.get(MediaFormat.KEY_DURATION));
			if (map.has(MediaFormat.KEY_CHANNEL_COUNT))
				format.setInteger(MediaFormat.KEY_CHANNEL_COUNT,
					(Integer) map.get(MediaFormat.KEY_CHANNEL_COUNT));
			if (map.has(MediaFormat.KEY_SAMPLE_RATE))
				format.setInteger(MediaFormat.KEY_SAMPLE_RATE,
					(Integer) map.get(MediaFormat.KEY_SAMPLE_RATE));
			if (map.has(MediaFormat.KEY_CHANNEL_MASK))
				format.setInteger(MediaFormat.KEY_CHANNEL_MASK,
					(Integer) map.get(MediaFormat.KEY_CHANNEL_MASK));
			if (map.has(MediaFormat.KEY_AAC_PROFILE))
				format.setInteger(MediaFormat.KEY_AAC_PROFILE,
					(Integer) map.get(MediaFormat.KEY_AAC_PROFILE));
			if (map.has(MediaFormat.KEY_AAC_SBR_MODE))
				format.setInteger(MediaFormat.KEY_AAC_SBR_MODE,
					(Integer) map.get(MediaFormat.KEY_AAC_SBR_MODE));
			if (map.has(MediaFormat.KEY_MAX_INPUT_SIZE))
				format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,
					(Integer) map.get(MediaFormat.KEY_MAX_INPUT_SIZE));
			if (map.has(MediaFormat.KEY_IS_ADTS))
				format.setInteger(MediaFormat.KEY_IS_ADTS,
					(Integer) map.get(MediaFormat.KEY_IS_ADTS));
			if (map.has("what"))
				format.setInteger("what", (Integer)map.get("what"));
			if (map.has("csd-0"))
				format.setByteBuffer("csd-0", asByteBuffer((String)map.get("csd-0")));
			if (map.has("csd-1"))
				format.setByteBuffer("csd-1", asByteBuffer((String)map.get("csd-1")));
			if (map.has("csd-2"))
				format.setByteBuffer("csd-2", asByteBuffer((String)map.get("csd-2")));
		} catch (final JSONException e) {
			Log.e(TAG, "writeFormat:" + format_str, e);
			format = null;
		}
		return format;
	}

	/**
	 * バイトバッファーの内容を文字列に変換するためのヘルパーメソッド
	 * @param buffer
	 * @return
	 */
	/*package*/ static final String asString(@NonNull final ByteBuffer buffer) {
		final byte[] temp = new byte[16];
		final StringBuilder sb = new StringBuilder();
		int n = (buffer != null ? buffer.limit() : 0);
		if (n > 0) {
			buffer.rewind();
			int sz = Math.min(n, 16);
			n -= sz;
			for (; sz > 0; sz = Math.min(n, 16), n -= sz) {
				buffer.get(temp, 0, sz);
				for (int i = 0; i < sz; i++) {
					sb.append(temp[i]).append(',');
				}
			}
		}
		return sb.toString();
	}

	/**
	 * 文字列表記から元のバイトバッファーに戻すためのヘルパーメソッド
	 * @param str
	 * @return
	 */
	/*package*/ static final ByteBuffer asByteBuffer(@NonNull final String str) {
		final String[] hex = str.split(",");
		final int m = hex.length;
		final byte[] temp = new byte[m];
		int n = 0;
		for (int i = 0; i < m; i++) {
			if (!TextUtils.isEmpty(hex[i]))
				temp[n++] = (byte)Integer.parseInt(hex[i]);
		}
		return (n > 0) ? ByteBuffer.wrap(temp, 0, n) : null;
	}
}
