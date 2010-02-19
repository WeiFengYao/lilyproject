/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.hbaseindex;

import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;

import java.io.IOException;

/**
 * A QueryResult on top of a HBase scanner.
 */
class ScannerQueryResult implements QueryResult {
    private ResultScanner scanner;
    private int indexKeyLength;
    private boolean invertKey;

    public ScannerQueryResult(ResultScanner scanner, int indexKeyLength, boolean invertKey) {
        this.scanner = scanner;
        this.indexKeyLength = indexKeyLength;
        this.invertKey = invertKey;
    }

    public byte[] next() throws IOException {
        Result result = scanner.next();
        if (result == null)
            return null;

        byte[] rowKey = result.getRow();
        byte[] targetKey = new byte[rowKey.length - indexKeyLength];
        System.arraycopy(rowKey, indexKeyLength, targetKey, 0, targetKey.length);

        if (invertKey) {
            for (int i = 0; i < targetKey.length; i++) {
                targetKey[i] ^= 0xFF;
            }
        }

        return targetKey;
    }
}
