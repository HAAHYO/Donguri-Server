package com.safeflow.global.exception

class CareActionNotFoundException : SafeFlowException(ErrorCode.CARE_ACTION_NOT_FOUND)

class CareActionAlreadySentException : SafeFlowException(ErrorCode.CARE_ACTION_ALREADY_SENT)

class CareActionStaleException : SafeFlowException(ErrorCode.CARE_ACTION_STALE)
