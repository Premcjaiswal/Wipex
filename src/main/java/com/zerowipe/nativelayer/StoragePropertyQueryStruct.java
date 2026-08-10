package com.zerowipe.nativelayer;

import com.sun.jna.Structure;
import java.util.List;

/**
 * {@code STORAGE_PROPERTY_QUERY} - the input buffer for
 * {@code IOCTL_STORAGE_QUERY_PROPERTY}.
 *
 * <pre>
 * typedef struct _STORAGE_PROPERTY_QUERY {
 *   STORAGE_PROPERTY_ID PropertyId;       // 4 bytes
 *   STORAGE_QUERY_TYPE  QueryType;        // 4 bytes
 *   UCHAR                AdditionalParameters[1];
 * } STORAGE_PROPERTY_QUERY;
 * </pre>
 *
 * <p>None of the queries this gateway issues need additional parameters,
 * so a 4-byte placeholder array is used instead of the flexible 1-byte
 * array member - the extra padding is harmless and keeps the struct
 * naturally 4-byte aligned.
 */
final class StoragePropertyQueryStruct extends Structure {

    public int propertyId;
    public int queryType;
    public byte[] additionalParameters = new byte[4];

    @Override
    protected List<String> getFieldOrder() {
        return List.of("propertyId", "queryType", "additionalParameters");
    }
}
