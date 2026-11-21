package com.donguri.transaction

import com.donguri.global.error.DonguriException
import com.donguri.global.error.ErrorCode

class TransactionNotFoundException : DonguriException(ErrorCode.TRANSACTION_NOT_FOUND)

class TransactionNotBlockedException : DonguriException(ErrorCode.TRANSACTION_NOT_BLOCKED)

class TransactionAlreadyJudgedException : DonguriException(ErrorCode.TRANSACTION_ALREADY_JUDGED)
