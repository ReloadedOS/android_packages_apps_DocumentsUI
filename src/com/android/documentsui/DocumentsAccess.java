/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import android.annotation.Nullable;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.util.Log;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides synchronous access to {@link DocumentInfo} instances given some identifying information
 * and some documents API.
 */
public interface DocumentsAccess {

    @Nullable DocumentInfo getRootDocument(RootInfo root);
    @Nullable DocumentInfo getDocument(Uri uri);
    @Nullable DocumentInfo getArchiveDocument(Uri uri);

    boolean isDocumentUri(Uri uri);
    @Nullable Path findPath(Uri uri) throws RemoteException;

    List<DocumentInfo> getDocuments(String authority, List<String> docIds) throws RemoteException;

    public static DocumentsAccess create(Context context) {
        return new RuntimeDocumentAccess(context);
    }

    public final class RuntimeDocumentAccess implements DocumentsAccess {

        private static final String TAG = "DocumentAccess";

        private final Context mContext;

        private RuntimeDocumentAccess(Context context) {
            mContext = context;
        }

        @Override
        public @Nullable DocumentInfo getRootDocument(RootInfo root) {
            return getDocument(
                    DocumentsContract.buildDocumentUri(root.authority, root.documentId));
        }

        @Override
        public @Nullable DocumentInfo getDocument(Uri uri) {
            try {
                return DocumentInfo.fromUri(mContext.getContentResolver(), uri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Couldn't create DocumentInfo for uri: " + uri);
            }

            return null;
        }

        @Override
        public List<DocumentInfo> getDocuments(String authority, List<String> docIds)
                throws RemoteException {

            try(final ContentProviderClient client = DocumentsApplication
                    .acquireUnstableProviderOrThrow(mContext.getContentResolver(), authority)) {

                List<DocumentInfo> result = new ArrayList<>(docIds.size());
                for (String docId : docIds) {
                    final Uri uri = DocumentsContract.buildDocumentUri(authority, docId);
                    try (final Cursor cursor = client.query(uri, null, null, null, null)) {
                        if (!cursor.moveToNext()) {
                            Log.e(TAG, "Couldn't create DocumentInfo for Uri: " + uri);
                            throw new RemoteException("Failed to move cursor.");
                        }

                        result.add(DocumentInfo.fromCursor(cursor, authority));
                    }
                }

                return result;
            }
        }

        @Override
        public DocumentInfo getArchiveDocument(Uri uri) {
            return getDocument(ArchivesProvider.buildUriForArchive(uri));
        }

        @Override
        public boolean isDocumentUri(Uri uri) {
            return DocumentsContract.isDocumentUri(mContext, uri);
        }

        @Override
        public Path findPath(Uri docUri) throws RemoteException {
            final ContentResolver resolver = mContext.getContentResolver();
            try (final ContentProviderClient client = DocumentsApplication
                    .acquireUnstableProviderOrThrow(resolver, docUri.getAuthority())) {
                return DocumentsContract.findPath(client, docUri);
            }
        }
    }
}
