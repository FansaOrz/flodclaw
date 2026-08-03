package com.foldledger.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    private val parser = PaymentNotificationParser()

    @Test
    fun parseWechatPayment() {
        val result = parser.parse(
            PaymentNotificationParser.PKG_WECHAT,
            "微信支付",
            "你已向星巴克付款¥28.00",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(2800L, result!!.amountFen)
    }

    @Test
    fun parseAlipayPayment() {
        val result = parser.parse(
            PaymentNotificationParser.PKG_ALIPAY,
            "支付成功",
            "成功向美团外卖付款￥35.5",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(3550L, result!!.amountFen)
    }

    @Test
    fun ignoreNonPayment() {
        val result = parser.parse(
            PaymentNotificationParser.PKG_WECHAT,
            "微信",
            "你收到一条消息",
            1_700_000_000_000L,
        )
        assertNull(result)
    }

    @Test
    fun parseAlipayUi() {
        val result = parser.parseAlipayUiText(
            "支付成功 付给瑞幸咖啡 ￥18.00 完成",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(1800L, result!!.amountFen)
    }

    @Test
    fun parseWechatOutgoingTransfer() {
        val result = parser.parse(
            PaymentNotificationParser.PKG_WECHAT,
            "微信",
            "你已向张三转账¥50.00",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(5000L, result!!.amountFen)
        assertEquals("张三", result.merchant)
        assertEquals(com.foldledger.domain.model.MoneyDirection.EXPENSE, result.direction)
    }

    @Test
    fun parseWechatIncomingTransfer() {
        val result = parser.parse(
            PaymentNotificationParser.PKG_WECHAT,
            "微信支付",
            "李四向你转账￥20.00，点击收款",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(2000L, result!!.amountFen)
        assertEquals(com.foldledger.domain.model.MoneyDirection.INCOME, result.direction)
    }

    @Test
    fun parseWechatTransferSuccessScreen() {
        val result = parser.parseWechatUiText(
            "转账成功 待对方确认收款 张三 ¥66.00 完成",
            1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(6600L, result!!.amountFen)
        assertEquals(com.foldledger.domain.model.CaptureSource.WECHAT_A11Y, result.source)
    }
}
