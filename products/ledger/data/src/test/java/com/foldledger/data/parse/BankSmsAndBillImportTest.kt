package com.foldledger.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsAndBillImportTest {
    private val sms = BankSmsParser()
    private val bills = BillCsvImporter()

    @Test
    fun parseIcbcExpenseSms() {
        val r = sms.parse(
            "95588",
            "【工商银行】您尾号1234卡25日15:20支出(消费)88.50元，余额1000.00元。",
            1_700_000_000_000L,
        )
        assertNotNull(r)
        assertEquals(8850L, r!!.amountFen)
        assertEquals(com.foldledger.domain.model.MoneyDirection.EXPENSE, r.direction)
    }

    @Test
    fun parseWechatBillCsv() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2024-01-02 12:00:00,商户消费,星巴克,咖啡,支出,32.00,零钱,支付成功,10001,20001,
            2024-01-03 08:00:00,转账,张三,转账,支出,50.00,零钱,对方已收钱,10002,20002,
        """.trimIndent()
        val (source, list) = bills.parse(csv)
        assertEquals(com.foldledger.domain.model.CaptureSource.WECHAT_IMPORT, source)
        assertTrue(list.size >= 2)
        assertEquals(3200L, list[0].amountFen)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"), java.util.Locale.CHINA)
        cal.timeInMillis = list[0].capturedAt
        assertEquals(2024, cal.get(java.util.Calendar.YEAR))
        assertEquals(0, cal.get(java.util.Calendar.MONTH)) // January
        assertEquals(2, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(12, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun parseExcelSerialDateInTimeColumn() {
        // 45432.5 ≈ 2024-05-20 12:00 (Excel serial, Asia/Shanghai)
        val csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),当前状态,交易单号
            45432.5,商户消费,滴滴出行,出行,支出,18.00,支付成功,EXCEL001
        """.trimIndent()
        val (_, list) = bills.parse(csv)
        assertEquals(1, list.size)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"), java.util.Locale.CHINA)
        cal.timeInMillis = list[0].capturedAt
        assertEquals(2024, cal.get(java.util.Calendar.YEAR))
        assertEquals(4, cal.get(java.util.Calendar.MONTH)) // May
        assertEquals(20, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
