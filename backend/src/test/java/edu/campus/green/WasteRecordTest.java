package edu.campus.green;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WasteRecordTest {
    @Test
    void partialCollectionKeepsRemainderPendingAndCannotOvercollect() {
        var location = new CampusLocation("Demo location", null, true);
        var user = new AppUser("demo", "unused", "Demo User", Role.STAFF);
        var record = new WasteRecord(WasteRecord.Category.PLASTIC, "Bottles", new BigDecimal("10.000"),
            WasteRecord.Unit.KG, location, user, LocalDate.now(), WasteRecord.Segregation.PROPER, null, true);

        record.addCollection(new BigDecimal("4.000"));
        assertEquals(new BigDecimal("6.000"), record.getRemainingQuantity());
        assertEquals(WasteRecord.Collection.PENDING, record.getCollectionStatus());
        assertThrows(IllegalArgumentException.class, () -> record.addCollection(new BigDecimal("7.000")));

        record.addCollection(new BigDecimal("6.000"));
        assertEquals(WasteRecord.Collection.COLLECTED, record.getCollectionStatus());
    }

    @Test
    void handoverCannotExceedCollectedQuantityAndIsSeparateFromCollection() {
        var record = new WasteRecord(WasteRecord.Category.E_WASTE, "Keyboard", new BigDecimal("3"),
            WasteRecord.Unit.ITEM, new CampusLocation("Demo location", null, true),
            new AppUser("demo", "unused", "Demo User", Role.STAFF), LocalDate.now(),
            WasteRecord.Segregation.PROPER, null, true);
        record.addCollection(new BigDecimal("3"));
        assertEquals(WasteRecord.Collection.COLLECTED, record.getCollectionStatus());
        assertThrows(IllegalArgumentException.class, () -> record.addHandover(new BigDecimal("4")));
        record.addHandover(new BigDecimal("2"));
        assertEquals(WasteRecord.Collection.COLLECTED, record.getCollectionStatus());
        record.addHandover(new BigDecimal("1"));
        assertEquals(WasteRecord.Collection.HANDED_OVER, record.getCollectionStatus());
    }

    @Test
    void eWasteInventoryTracksConditionAndStorageSeparatelyFromItemQuantity() {
        var record = new WasteRecord(WasteRecord.Category.E_WASTE, "Monitor", new BigDecimal("2"),
            WasteRecord.Unit.ITEM, new CampusLocation("Demo location", null, true),
            new AppUser("demo", "unused", "Demo User", Role.STAFF), LocalDate.now(),
            WasteRecord.Segregation.PROPER, null, true);
        var inventory = new EWasteItem(record, EWasteItem.Condition.DAMAGED, "Locked storage cabinet");

        assertEquals(new BigDecimal("2"), inventory.getRecord().getQuantity());
        assertEquals(EWasteItem.Condition.DAMAGED, inventory.getCondition());
        assertEquals("Locked storage cabinet", inventory.getStorageLocation());
        assertNull(inventory.getHandoverDate());
        inventory.recordHandover(LocalDate.now(), "Authorized recycler");
        assertEquals("Authorized recycler", inventory.getAuthorizedRecipient());
    }
}
