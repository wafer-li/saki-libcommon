package com.serenegiant.glutils;
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

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import com.serenegiant.egl.EGLBase;
import com.serenegiant.math.Fraction;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 分配描画インターフェース
 * FIXME GLPipeline/IPipelienSourceを使うIRendererHolder実装を作る
 */
public interface IRendererHolder extends IMirror {
    public static final int DEFAULT_CAPTURE_COMPRESSION = 80;

    public static final int OUTPUT_FORMAT_JPEG = 0;    // Bitmap.CompressFormat.JPEG
    public static final int OUTPUT_FORMAT_PNG = 1;    // Bitmap.CompressFormat.PNG
    public static final int OUTPUT_FORMAT_WEBP = 2;    // Bitmap.CompressFormat.WEBP

    @IntDef({ OUTPUT_FORMAT_JPEG, OUTPUT_FORMAT_PNG, OUTPUT_FORMAT_WEBP })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StillCaptureFormat {
    }

    /**
     * IRendererHolderからのコールバックリスナー
     */
    public interface RenderHolderCallback {
        public void onCreate(Surface surface);

        public void onFrameAvailable();

        public void onDestroy();
    }

    public interface OnCapturedListener {
        public void onCaptured(@NonNull final IRendererHolder rendererHolder, final boolean success);
    }

    /**
     * 実行中かどうか
     */
    public boolean isRunning();

    /**
     * 関係するすべてのリソースを開放する。再利用できない
     */
    public void release();

    @Nullable
    public EGLBase.IContext<?> getContext();

    /**
     * マスター用の映像を受け取るためのSurfaceを取得
     */
    public Surface getSurface();

    /**
     * マスター用の映像を受け取るためのSurfaceTextureを取得
     */
    public SurfaceTexture getSurfaceTexture();

    /**
     * マスター用の映像を受け取るためのマスターをチェックして無効なら再生成要求する
     */
    public void reset();

    /**
     * マスター映像サイズをサイズ変更要求
     */
    public void resize(final int width, final int height) throws IllegalStateException;

    /**
     * 分配描画用のSurfaceを追加
     * このメソッドは指定したSurfaceが追加されるか
     * interruptされるまでカレントスレッドをブロックする。
     *
     * @param id 普通は#hashCodeを使う
     * @param surface Surface/SurfaceHolder/SurfaceTexture/SurfaceView/TextureWrapperのいずれか
     */
    public void addSurface(final int id, final Object surface, final boolean isRecordable)
        throws IllegalStateException, IllegalArgumentException;

    /**
     * 分配描画用のSurfaceを追加
     * このメソッドは指定したSurfaceが追加されるか
     * interruptされるまでカレントスレッドをブロックする。
     *
     * @param id 普通は#hashCodeを使う
     * @param surface Surface/SurfaceHolder/SurfaceTexture/SurfaceView/TextureWrapperのいずれか
     * @param maxFps nullまたは0以下なら制限しない
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    public void addSurface(final int id, final Object surface, final boolean isRecordable,
        @Nullable final Fraction maxFps) throws IllegalStateException, IllegalArgumentException;

    /**
     * 分配描画用のSurfaceを追加
     * このメソッドは指定したSurfaceが追加されるか
     * interruptされるまでカレントスレッドをブロックする。
     *
     * @param id 普通は#hashCodeを使う
     * @param surface Surface/SurfaceHolder/SurfaceTexture/SurfaceView/TextureWrapperのいずれか
     * @param maxFps nullまたは0以下なら制限しない
     * @param removeClearColor 0以下ならclearしない
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    public void addSurface(final int id, final Object surface, final boolean isRecordable,
        @Nullable final Fraction maxFps, final int removeClearColor)
        throws IllegalStateException, IllegalArgumentException;

    /**
     * 分配描画用のSurfaceを削除
     * このメソッドは指定したSurfaceが削除されるか
     * interruptされるまでカレントスレッドをブロックする。
     */
    public void removeSurface(final int id);

    /**
     * 分配描画用のSurfaceを全て削除
     * このメソッドはSurfaceが削除されるか
     * interruptされるまでカレントスレッドをブロックする。
     */
    public void removeSurfaceAll();

    /**
     * 分配描画用のSurfaceを指定した色で塗りつぶす
     */
    public void clearSurface(final int id, final int color);

    /**
     * 分配描画用のSurfaceを指定した色で塗りつぶす
     */
    public void clearSurfaceAll(final int color);

    /**
     * モデルビュー変換行列をセット
     *
     * @param matrix offset以降に16要素以上
     */
    public void setMvpMatrix(final int id, final int offset, @NonNull @Size(min = 16) final float[] matrix);

    /**
     * 分配描画用のSurfaceへの描画が有効かどうかを取得
     */
    public boolean isEnabled(final int id);

    /**
     * 分配描画用のSurfaceへの描画の有効・無効を切替
     */
    public void setEnabled(final int id, final boolean enable);

    /**
     * 強制的に現在の最新のフレームを描画要求する
     * 分配描画用Surface全てが更新されるので注意
     */
    public void requestFrame();

    /**
     * 追加されている分配描画用のSurfaceの数を取得
     */
    public int getCount();

    /**
     * 静止画を撮影する
     * 撮影完了を待機する
     *
     * @deprecated GL|ESのテクスチャをBitmapとしてキャプチャするための
     * ImageReader(GLBitmapImageReader)を追加したのでIRenderer自体での
     * 静止画キャプチャ機能は削除する予定
     */
    @Deprecated
    public void captureStill(@NonNull final String path, @Nullable final OnCapturedListener listener)
        throws FileNotFoundException, IllegalStateException;

    /**
     * 静止画を撮影する
     * 撮影完了を待機する
     *
     * @param captureCompression JPEGの圧縮率, pngの時は無視
     * @deprecated GL|ESのテクスチャをBitmapとしてキャプチャするための
     * ImageReader(GLBitmapImageReader)を追加したのでIRenderer自体での
     * 静止画キャプチャ機能は削除する予定
     */
    @Deprecated
    public void captureStill(@NonNull final String path, @IntRange(from = 1L, to = 99L) final int captureCompression,
        @Nullable final OnCapturedListener listener) throws FileNotFoundException, IllegalStateException;

    /**
     * 静止画を撮影する
     * 撮影完了を待機する
     *
     * @deprecated GL|ESのテクスチャをBitmapとしてキャプチャするための
     * ImageReader(GLBitmapImageReader)を追加したのでIRenderer自体での
     * 静止画キャプチャ機能は削除する予定
     */
    @Deprecated
    public void captureStill(@NonNull final OutputStream out, @StillCaptureFormat final int stillCaptureFormat,
        @IntRange(from = 1L, to = 99L) final int captureCompression, @Nullable final OnCapturedListener listener)
        throws IllegalStateException;

    /**
     * レンダリングスレッド上で指定したタスクを実行する
     */
    public void queueEvent(@NonNull final Runnable task);
}
