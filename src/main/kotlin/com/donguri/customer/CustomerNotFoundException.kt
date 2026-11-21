package com.donguri.customer

import com.donguri.global.error.DonguriException
import com.donguri.global.error.ErrorCode

class CustomerNotFoundException : DonguriException(ErrorCode.CUSTOMER_NOT_FOUND)
