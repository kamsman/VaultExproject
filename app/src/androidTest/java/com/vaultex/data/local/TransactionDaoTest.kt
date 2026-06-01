package com.vaultex.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.db.VaultExDatabase
import com.vaultex.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: VaultExDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultExDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun tx(hash: String, status: String = "PENDING") = TransactionEntity(
        hash = hash, type = "SEND", blockchain = "ETH",
        fromAddress = "0xFrom", toAddress = "0xTo",
        amount = "0.1", tokenSymbol = "ETH", fee = "",
        status = status, timestamp = System.currentTimeMillis(),
        confirmations = 0, blockNumber = null
    )

    @Test
    fun insertAndObserve() = runTest {
        dao.insert(tx("0xabc"))
        val list = dao.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("0xabc", list.first().hash)
    }

    @Test
    fun multipleInsertOrderedByTimestamp() = runTest {
        dao.insert(tx("0x001").copy(timestamp = 1000L))
        dao.insert(tx("0x002").copy(timestamp = 3000L))
        dao.insert(tx("0x003").copy(timestamp = 2000L))
        val list = dao.observeAll().first()
        assertEquals("0x002", list[0].hash)
        assertEquals("0x003", list[1].hash)
        assertEquals("0x001", list[2].hash)
    }

    @Test
    fun updateStatusToConfirmed() = runTest {
        dao.insert(tx("0xdef"))
        dao.updateStatus("0xdef", "CONFIRMED", 1)
        val tx = dao.observeAll().first().first { it.hash == "0xdef" }
        assertEquals("CONFIRMED", tx.status)
        assertEquals(1, tx.confirmations)
    }

    @Test
    fun updateStatusToFailed() = runTest {
        dao.insert(tx("0xfail"))
        dao.updateStatus("0xfail", "FAILED", 0)
        val tx = dao.observeAll().first().first { it.hash == "0xfail" }
        assertEquals("FAILED", tx.status)
    }

    @Test
    fun replaceOnConflict() = runTest {
        dao.insert(tx("0xdup", "PENDING"))
        dao.insert(tx("0xdup", "CONFIRMED"))
        val list = dao.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("CONFIRMED", list.first().status)
    }

    @Test
    fun observeByAddress() = runTest {
        dao.insert(tx("0xa1").copy(fromAddress = "0xMyAddr"))
        dao.insert(tx("0xa2").copy(toAddress   = "0xMyAddr"))
        dao.insert(tx("0xa3").copy(fromAddress = "0xOther", toAddress = "0xOther2"))
        val mine = dao.observeByAddress("0xMyAddr").first()
        assertEquals(2, mine.size)
        assertTrue(mine.none { it.hash == "0xa3" })
    }
}
