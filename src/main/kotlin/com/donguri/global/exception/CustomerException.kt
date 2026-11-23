package com.donguri.global.exception

class CustomerNotFoundException : DonguriException(ErrorCode.CUSTOMER_NOT_FOUND)

class InsufficientBalanceException : DonguriException(ErrorCode.INSUFFICIENT_BALANCE)
