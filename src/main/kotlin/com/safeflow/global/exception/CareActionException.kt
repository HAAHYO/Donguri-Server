package com.safeflow.global.exception

class CareActionNotFoundException : DonguriException(ErrorCode.CARE_ACTION_NOT_FOUND)

class CareActionAlreadySentException : DonguriException(ErrorCode.CARE_ACTION_ALREADY_SENT)

class CareActionStaleException : DonguriException(ErrorCode.CARE_ACTION_STALE)
