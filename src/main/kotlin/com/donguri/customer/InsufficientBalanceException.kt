package com.donguri.customer

import com.donguri.global.error.DonguriException
import com.donguri.global.error.ErrorCode

class InsufficientBalanceException : DonguriException(ErrorCode.INSUFFICIENT_BALANCE)
