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
package org.lilycms.repository.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lilycms.repository.api.BlobStoreAccessFactory;
import org.lilycms.repository.api.FieldType;
import org.lilycms.repository.api.IdGenerator;
import org.lilycms.repository.api.QName;
import org.lilycms.repository.api.Record;
import org.lilycms.repository.api.RecordType;
import org.lilycms.repository.api.Repository;
import org.lilycms.repository.api.Scope;
import org.lilycms.repository.api.TypeManager;
import org.lilycms.repository.api.exception.FieldNotFoundException;
import org.lilycms.repository.api.exception.InvalidRecordException;
import org.lilycms.repository.api.exception.RecordExistsException;
import org.lilycms.repository.api.exception.RecordNotFoundException;
import org.lilycms.repository.api.exception.RecordTypeNotFoundException;
import org.lilycms.repository.impl.DFSBlobStoreAccess;
import org.lilycms.repository.impl.HBaseRepository;
import org.lilycms.repository.impl.HBaseTypeManager;
import org.lilycms.repository.impl.IdGeneratorImpl;
import org.lilycms.repository.impl.SizeBasedBlobStoreAccessFactory;
import org.lilycms.testfw.TestHelper;

public class HBaseRepositoryTest {

    private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
    private static IdGenerator idGenerator = new IdGeneratorImpl();
    private static TypeManager typeManager;
    private static Repository repository;
    private static FieldType fieldType1;
    private static FieldType fieldType1B;
    private static FieldType fieldType2;
    private static FieldType fieldType3;
    private static FieldType fieldType4;
    private static FieldType fieldType5;
    private static FieldType fieldType6;
    private static RecordType recordType1;
    private static RecordType recordType1B;
    private static RecordType recordType2;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestHelper.setupLogging();
        TEST_UTIL.startMiniCluster(1);
        typeManager = new HBaseTypeManager(idGenerator, TEST_UTIL.getConfiguration());
        DFSBlobStoreAccess dfsBlobStoreAccess = new DFSBlobStoreAccess(TEST_UTIL.getDFSCluster().getFileSystem());
        BlobStoreAccessFactory blobStoreOutputStreamFactory = new SizeBasedBlobStoreAccessFactory(dfsBlobStoreAccess);
        repository = new HBaseRepository(typeManager, idGenerator, blobStoreOutputStreamFactory , TEST_UTIL.getConfiguration());
        setupTypes();
    }

    private static void setupTypes() throws Exception {
        setupFieldTypes();
        setupRecordTypes();
    }

    private static void setupFieldTypes() throws Exception {
        String namespace = "/test/repository";
        fieldType1 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("STRING", false,
                        false), new QName(namespace, "field1"), Scope.NON_VERSIONED));
        fieldType1B = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("STRING", false,
                        false), new QName(namespace, "field1B"), Scope.NON_VERSIONED));
        fieldType2 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("INTEGER", false,
                        false), new QName(namespace, "field2"), Scope.VERSIONED));
        fieldType3 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("BOOLEAN", false,
                        false), new QName(namespace, "field3"), Scope.VERSIONED_MUTABLE));

        fieldType4 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("INTEGER", false,
                        false), new QName(namespace, "field4"), Scope.NON_VERSIONED));
        fieldType5 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("BOOLEAN", false,
                        false), new QName(namespace, "field5"), Scope.VERSIONED));
        fieldType6 = typeManager.createFieldType(typeManager.newFieldType(typeManager.getValueType("STRING", false,
                        false), new QName(namespace, "field6"), Scope.VERSIONED_MUTABLE));

    }

    private static void setupRecordTypes() throws Exception {
        recordType1 = typeManager.newRecordType("RT1");
        recordType1.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType1.getId(), false));
        recordType1.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType2.getId(), false));
        recordType1.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType3.getId(), false));
        recordType1 = typeManager.createRecordType(recordType1);

        recordType1B = recordType1.clone();
        recordType1B.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType1B.getId(), true));
        recordType1B = typeManager.updateRecordType(recordType1B);

        recordType2 = typeManager.newRecordType("RT2");

        recordType2.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType4.getId(), false));
        recordType2.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType5.getId(), false));
        recordType2.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType6.getId(), false));
        recordType2 = typeManager.createRecordType(recordType2);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        TEST_UTIL.shutdownMiniCluster();
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRecordCreateWithoutRecordType() throws Exception {
        Record record = repository.newRecord(idGenerator.newRecordId());
        try {
            record = repository.create(record);
        } catch (InvalidRecordException expected) {
        }
    }

    @Test
    public void testRecordUpdateWithoutRecordType() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        try {
            record = repository.update(updateRecord);
            fail();
        } catch (InvalidRecordException expected) {
        }
    }

    @Test
    public void testEmptyRecordCreate() throws Exception {
        Record record = repository.newRecord();
        record.setRecordType(recordType1.getId(), null);
        try {
            record = repository.create(record);
        } catch (InvalidRecordException expected) {
        }
    }

    @Test
    public void testCreate() throws Exception {
        Record createdRecord = createDefaultRecord();

        assertEquals(Long.valueOf(1), createdRecord.getVersion());
        assertEquals("value1", createdRecord.getField(fieldType1.getName()));
        assertEquals(123, createdRecord.getField(fieldType2.getName()));
        assertTrue((Boolean) createdRecord.getField(fieldType3.getName()));
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId());
        assertEquals(Long.valueOf(1), createdRecord.getRecordTypeVersion());
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(Long.valueOf(1), createdRecord.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(Long.valueOf(1), createdRecord.getRecordTypeVersion(Scope.VERSIONED));
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertEquals(Long.valueOf(1), createdRecord.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));

        assertEquals(createdRecord, repository.read(createdRecord.getId()));
    }

    private Record createDefaultRecord() throws Exception {
        Record record = repository.newRecord();
        record.setRecordType(recordType1.getId(), recordType1.getVersion());
        record.setField(fieldType1.getName(), "value1");
        record.setField(fieldType2.getName(), 123);
        record.setField(fieldType3.getName(), true);
        return repository.create(record);
    }

    @Test
    public void testCreateExistingRecordFails() throws Exception {
        Record record = createDefaultRecord();

        try {
            repository.create(record);
            fail();
        } catch (RecordExistsException expected) {
        }
    }

    @Test
    public void testCreateWithNonExistingRecordTypeFails() throws Exception {
        Record record = repository.newRecord(idGenerator.newRecordId());
        record.setRecordType("nonExistingRecordType", null);
        record.setField(fieldType1.getName(), "value1");
        try {
            repository.create(record);
            fail();
        } catch (RecordTypeNotFoundException expected) {
        }
    }

    @Test
    public void testCreateUsesLatestRecordType() throws Exception {
        Record record = repository.newRecord();
        record.setRecordType(recordType1.getId(), null);
        record.setField(fieldType1.getName(), "value1");
        Record createdRecord = repository.create(record);
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId());
        assertEquals(Long.valueOf(2), createdRecord.getRecordTypeVersion());
        assertEquals(recordType1.getId(), createdRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(Long.valueOf(2), createdRecord.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertNull(createdRecord.getRecordTypeId(Scope.VERSIONED));
        assertNull(createdRecord.getRecordTypeVersion(Scope.VERSIONED));
        assertNull(createdRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertNull(createdRecord.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));

        assertEquals(createdRecord, repository.read(createdRecord.getId()));
    }

    @Test
    public void testCreateVariant() throws Exception {
        Record record = createDefaultRecord();

        Map<String, String> variantProperties = new HashMap<String, String>();
        variantProperties.put("dimension1", "dimval1");
        Record variant = repository.newRecord(idGenerator.newRecordId(record.getId(), variantProperties));
        variant.setRecordType(recordType1.getId(), null);
        variant.setField(fieldType1.getName(), "value2");
        variant.setField(fieldType2.getName(), 567);
        variant.setField(fieldType3.getName(), false);

        Record createdVariant = repository.create(variant);

        assertEquals(Long.valueOf(1), createdVariant.getVersion());
        assertEquals("value2", createdVariant.getField(fieldType1.getName()));
        assertEquals(567, createdVariant.getField(fieldType2.getName()));
        assertFalse((Boolean) createdVariant.getField(fieldType3.getName()));

        assertEquals(createdVariant, repository.read(variant.getId()));
    }

    @Test
    public void testUpdate() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);

        Record updatedRecord = repository.update(updateRecord);

        assertEquals(Long.valueOf(2), updatedRecord.getVersion());
        assertEquals("value2", updatedRecord.getField(fieldType1.getName()));
        assertEquals(789, updatedRecord.getField(fieldType2.getName()));
        assertEquals(false, updatedRecord.getField(fieldType3.getName()));

        assertEquals(updatedRecord, repository.read(record.getId()));
    }

    @Test
    public void testUpdateOnlyOneField() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        updateRecord.setField(fieldType1.getName(), "value2");

        Record updatedRecord = repository.update(updateRecord);

        assertEquals(Long.valueOf(1), updatedRecord.getVersion());
        assertEquals("value2", updatedRecord.getField(fieldType1.getName()));
        try {
            updatedRecord.getField(fieldType2.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        try {
            updatedRecord.getField(fieldType3.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        Record readRecord = repository.read(record.getId());
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        assertEquals(true, readRecord.getField(fieldType3.getName()));
    }

    @Test
    public void testEmptyUpdate() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());

        Record updatedRecord = repository.update(updateRecord);

        assertEquals(Long.valueOf(1), updatedRecord.getVersion());
        try {
            updatedRecord.getField(fieldType1.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        try {
            updatedRecord.getField(fieldType2.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        try {
            updatedRecord.getField(fieldType3.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        assertEquals(record, repository.read(record.getId()));
    }

    @Test
    public void testIdempotentUpdate() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();

        Record updatedRecord = repository.update(updateRecord);

        assertEquals(Long.valueOf(1), updatedRecord.getVersion());
        assertEquals("value1", updatedRecord.getField(fieldType1.getName()));
        assertEquals(123, updatedRecord.getField(fieldType2.getName()));
        assertEquals(true, updatedRecord.getField(fieldType3.getName()));

        assertEquals(record, repository.read(record.getId()));
    }

    @Test
    public void testUpdateIgnoresVersion() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setVersion(Long.valueOf(99));
        updateRecord.setField(fieldType1.getName(), "value2");

        Record updatedRecord = repository.update(updateRecord);

        assertEquals(Long.valueOf(1), updatedRecord.getVersion());

        assertEquals(updatedRecord, repository.read(record.getId()));
    }

    @Test
    public void testUpdateNonVersionable() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), null);
        updateRecord.setField(fieldType1.getName(), "aNewValue");
        repository.update(updateRecord);

        Record readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(1), readRecord.getVersion());
        assertEquals("aNewValue", readRecord.getField(fieldType1.getName()));
    }

    @Test
    public void testReadOlderVersions() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);

        repository.update(updateRecord);

        record.setField(fieldType1.getName(), "value2");
        assertEquals(record, repository.read(record.getId(), Long.valueOf(1)));
    }

    @Test
    public void testReadNonExistingRecord() throws Exception {
        try {
            repository.read(idGenerator.newRecordId());
            fail();
        } catch (RecordNotFoundException expected) {
        }
    }

    @Test
    public void testReadTooRecentRecord() throws Exception {
        Record record = createDefaultRecord();
        try {
            repository.read(record.getId(), Long.valueOf(2));
            fail();
        } catch (RecordNotFoundException expected) {
        }
    }

    @Test
    public void testReadSpecificFields() throws Exception {
        Record record = createDefaultRecord();
        Record readRecord = repository.read(record.getId(), Arrays.asList(new QName[] { fieldType1.getName(),
                fieldType2.getName(), fieldType3.getName() }));
        assertEquals(repository.read(record.getId()), readRecord);
    }

    @Test
    public void testUpdateWithNewRecordTypeVersion() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(recordType1B.getId(), recordType1B.getVersion());
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);

        Record updatedRecord = repository.update(updateRecord);
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId());
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion());
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED));
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));

        Record recordV1 = repository.read(record.getId(), Long.valueOf(1));
        assertEquals(recordType1B.getId(), recordV1.getRecordTypeId());
        assertEquals(recordType1B.getVersion(), recordV1.getRecordTypeVersion());
        assertEquals(recordType1B.getId(), recordV1.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType1B.getVersion(), recordV1.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertEquals(recordType1.getId(), recordV1.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType1.getVersion(), recordV1.getRecordTypeVersion(Scope.VERSIONED));
        assertEquals(recordType1.getId(), recordV1.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertEquals(recordType1.getVersion(), recordV1.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));
    }

    @Test
    public void testUpdateWithNewRecordTypeVersionOnlyOneFieldUpdated() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(recordType1B.getId(), recordType1B.getVersion());
        updateRecord.setField(fieldType2.getName(), 789);

        Record updatedRecord = repository.update(updateRecord);
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId());
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion());
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED));

        Record readRecord = repository.read(record.getId());
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId());
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion());
        assertEquals(recordType1B.getId(), readRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType1B.getVersion(), readRecord.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertEquals(recordType1B.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType1B.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED));
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertEquals(recordType1.getVersion(), readRecord.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));
    }

    @Test
    public void testUpdateWithNewRecordType() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(recordType2.getId(), recordType2.getVersion());
        updateRecord.setField(fieldType4.getName(), 1024);
        updateRecord.setField(fieldType5.getName(), false);
        updateRecord.setField(fieldType6.getName(), "value2");

        Record updatedRecord = repository.update(updateRecord);
        assertEquals(recordType2.getId(), updatedRecord.getRecordTypeId());
        assertEquals(recordType2.getVersion(), updatedRecord.getRecordTypeVersion());
        assertEquals(recordType2.getId(), updatedRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType2.getVersion(), updatedRecord.getRecordTypeVersion(Scope.NON_VERSIONED));
        assertEquals(recordType2.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType2.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED));
        assertEquals(recordType2.getId(), updatedRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
        assertEquals(recordType2.getVersion(), updatedRecord.getRecordTypeVersion(Scope.VERSIONED_MUTABLE));

        assertEquals(3, updatedRecord.getFields().size());

        Record readRecord = repository.read(record.getId());
        // Nothing got deleted
        assertEquals(6, readRecord.getFields().size());
        assertEquals("value1", readRecord.getField(fieldType1.getName()));
        assertEquals(1024, readRecord.getField(fieldType4.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        assertFalse((Boolean) readRecord.getField(fieldType5.getName()));
        assertTrue((Boolean) readRecord.getField(fieldType3.getName()));
        assertEquals("value2", readRecord.getField(fieldType6.getName()));
    }

    @Test
    public void testDeleteField() throws Exception {
        Record record = createDefaultRecord();
        Record deleteRecord = repository.newRecord(record.getId());
        deleteRecord.setRecordType(record.getRecordTypeId(), null);
        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType1.getName(), fieldType2.getName(),
                fieldType3.getName() }));

        repository.update(deleteRecord);
        Record readRecord = repository.read(record.getId());
        assertTrue(readRecord.getFields().isEmpty());
    }

    @Test
    public void testDeleteFieldsNoLongerInRecordType() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(recordType2.getId(), recordType2.getVersion());
        updateRecord.setField(fieldType4.getName(), 2222);
        updateRecord.setField(fieldType5.getName(), false);
        updateRecord.setField(fieldType6.getName(), "value2");

        repository.update(updateRecord);

        Record deleteRecord = repository.newRecord(record.getId());
        deleteRecord.setRecordType(recordType1.getId(), recordType1.getVersion());
        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType1.getName() }));
        repository.update(deleteRecord);

        Record readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(2), readRecord.getVersion());
        assertEquals(5, readRecord.getFields().size());
        try {
            readRecord.getField(fieldType1.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        assertEquals("value2", readRecord.getField(fieldType6.getName()));
        assertEquals(2222, readRecord.getField(fieldType4.getName()));

        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType2.getName(), fieldType3.getName() }));
        repository.update(deleteRecord);

        readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(3), readRecord.getVersion());
        assertEquals(3, readRecord.getFields().size());
        assertEquals(2222, readRecord.getField(fieldType4.getName()));
        assertEquals(false, readRecord.getField(fieldType5.getName()));
        assertEquals("value2", readRecord.getField(fieldType6.getName()));
    }

    @Test
    public void testUpdateAfterDelete() throws Exception {
        Record record = createDefaultRecord();
        Record deleteRecord = repository.newRecord(record.getId());
        deleteRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType2.getName() }));
        repository.update(deleteRecord);

        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        updateRecord.setField(fieldType2.getName(), 3333);
        repository.update(updateRecord);

        // Read version 3
        Record readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(3), readRecord.getVersion());
        assertEquals(3333, readRecord.getField(fieldType2.getName()));

        // Read version 2
        readRecord = repository.read(record.getId(), Long.valueOf(2));
        try {
            readRecord.getField(fieldType2.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        // Read version 1
        readRecord = repository.read(record.getId(), Long.valueOf(1));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
    }

    @Test
    public void testDeleteNonVersionableFieldAndUpdateVersionableField() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        updateRecord.setField(fieldType2.getName(), 999);
        updateRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType1.getName() }));
        repository.update(updateRecord);

        Record readRecord = repository.read(record.getId());
        assertEquals(999, readRecord.getField(fieldType2.getName()));
        try {
            readRecord.getField(fieldType1.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        readRecord = repository.read(record.getId(), Long.valueOf(1));
        try {
            readRecord.getField(fieldType1.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

    }

    @Test
    public void testUpdateAndDeleteSameField() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = repository.newRecord(record.getId());
        updateRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType2.getName() }));
        repository.update(updateRecord);

        try {
            repository.read(record.getId()).getField(fieldType2.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
    }

    @Test
    public void testDeleteRecord() throws Exception {
        Record record = createDefaultRecord();
        repository.delete(record.getId());
        try {
            repository.read(record.getId());
            fail();
        } catch (RecordNotFoundException expected) {
        }
    }

    @Test
    public void testUpdateMutableField() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);
        repository.update(updateRecord);

        // Read version 1
        Record readRecord = repository.read(record.getId(), Long.valueOf(1));
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        assertEquals(true, readRecord.getField(fieldType3.getName()));

        // Update mutable version 1
        updateRecord.setVersion(Long.valueOf(1));
        repository.updateMutableFields(updateRecord);

        // Read version 1 again
        readRecord = repository.read(record.getId(), Long.valueOf(1));
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        assertEquals(false, readRecord.getField(fieldType3.getName()));
    }

    @Test
    public void testUpdateMutableFieldWithNewRecordType() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);
        repository.update(updateRecord);

        Record updateMutableRecord = repository.newRecord(record.getId());
        updateMutableRecord.setVersion(Long.valueOf(1));
        updateMutableRecord.setRecordType(recordType2.getId(), recordType2.getVersion());
        updateMutableRecord.setField(fieldType4.getName(), 888);
        updateMutableRecord.setField(fieldType5.getName(), false);
        updateMutableRecord.setField(fieldType6.getName(), "value3");
        assertEquals(Long.valueOf(1), repository.updateMutableFields(updateMutableRecord).getVersion());

        Record readRecord = repository.read(record.getId(), Long.valueOf(1));
        assertEquals(Long.valueOf(1), readRecord.getVersion());
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        assertEquals(true, readRecord.getField(fieldType3.getName()));
        // Only the mutable fields got updated
        assertEquals("value3", readRecord.getField(fieldType6.getName()));
        try {
            readRecord.getField(fieldType4.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        try {
            readRecord.getField(fieldType5.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId());
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType2.getId(), readRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));

        readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(2), readRecord.getVersion());
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(789, readRecord.getField(fieldType2.getName()));
        assertEquals(false, readRecord.getField(fieldType3.getName()));
        assertEquals("value3", readRecord.getField(fieldType6.getName()));
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId());
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.NON_VERSIONED));
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.VERSIONED));
        assertEquals(recordType1.getId(), readRecord.getRecordTypeId(Scope.VERSIONED_MUTABLE));
    }

    @Test
    public void testDeleteMutableField() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord.setField(fieldType3.getName(), false);
        repository.update(updateRecord);

        Record deleteRecord = repository.newRecord(record.getId());
        deleteRecord.setVersion(Long.valueOf(1));
        deleteRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType1.getName(), fieldType2.getName(),
                fieldType3.getName() }));

        repository.updateMutableFields(deleteRecord);

        Record readRecord = repository.read(record.getId(), Long.valueOf(1));
        // The non-mutable fields were ignored
        assertEquals("value2", readRecord.getField(fieldType1.getName()));
        assertEquals(123, readRecord.getField(fieldType2.getName()));
        try {
            // The mutable field got deleted
            readRecord.getField(fieldType3.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        readRecord = repository.read(record.getId());
        assertEquals(false, readRecord.getField(fieldType3.getName()));
    }

    @Test
    public void testDeleteMutableFieldCopiesValueToNext() throws Exception {
        Record record = createDefaultRecord();
        Record updateRecord = record.clone();
        updateRecord.setField(fieldType1.getName(), "value2");
        updateRecord.setField(fieldType2.getName(), 789);
        updateRecord = repository.update(updateRecord); // Leave mutable field
                                                        // same on first update

        updateRecord.setField(fieldType3.getName(), false);
        updateRecord = repository.update(updateRecord);

        Record deleteRecord = repository.newRecord(record.getId());
        deleteRecord.setVersion(Long.valueOf(1));
        deleteRecord.setRecordType(record.getRecordTypeId(), record.getRecordTypeVersion());
        deleteRecord.addFieldsToDelete(Arrays.asList(new QName[] { fieldType3.getName() }));

        repository.updateMutableFields(deleteRecord);

        Record readRecord = repository.read(record.getId(), Long.valueOf(1));
        try {
            readRecord.getField(fieldType3.getName());
            fail();
        } catch (FieldNotFoundException expected) {
        }

        readRecord = repository.read(record.getId(), Long.valueOf(2));
        assertEquals(true, readRecord.getField(fieldType3.getName()));

        readRecord = repository.read(record.getId());
        assertEquals(false, readRecord.getField(fieldType3.getName()));
    }

    @Test
    public void testMixin() throws Exception {
        RecordType recordType4 = typeManager.newRecordType("RT4");
        recordType4.addFieldTypeEntry(typeManager.newFieldTypeEntry(fieldType6.getId(), false));
        recordType4.addMixin(recordType1.getId(), recordType1.getVersion());
        recordType4 = typeManager.createRecordType(recordType4);

        Record record = repository.newRecord(idGenerator.newRecordId());
        record.setRecordType(recordType4.getId(), recordType4.getVersion());
        record.setField(fieldType1.getName(), "foo");
        record.setField(fieldType2.getName(), 555);
        record.setField(fieldType3.getName(), true);
        record.setField(fieldType6.getName(), "bar");
        record = repository.create(record);

        Record readRecord = repository.read(record.getId());
        assertEquals(Long.valueOf(1), readRecord.getVersion());
        assertEquals("foo", readRecord.getField(fieldType1.getName()));
        assertEquals(555, readRecord.getField(fieldType2.getName()));
        assertEquals(true, readRecord.getField(fieldType3.getName()));
        assertEquals("bar", readRecord.getField(fieldType6.getName()));
    }
    
    @Test
    public void testNonVersionedToVersioned() throws Exception {
        // Create a record with only a versioned and non-versioned field
        Record record = repository.newRecord();
        record.setRecordType(recordType1.getId(), recordType1.getVersion());
        record.setField(fieldType1.getName(), "hello");
        record.setField(fieldType2.getName(), new Integer(4));
        record = repository.create(record);

        // Try to read the created version
        record = repository.read(record.getId(), 1L);
    }
}
