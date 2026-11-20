package com.donguri.auth

import com.donguri.global.error.DonguriException
import com.donguri.global.error.ErrorCode

class InvalidCredentialsException : DonguriException(ErrorCode.INVALID_CREDENTIALS)
