package com.danzucker.stitchpad.core.domain.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResultTest {

    @Test
    fun mapTransformsSuccessValue() {
        val result: Result<Int, TestError> = Result.Success(5)
        val mapped = result.map { it * 2 }
        assertIs<Result.Success<Int>>(mapped)
        assertEquals(10, mapped.data)
    }

    @Test
    fun mapPreservesError() {
        val result: Result<Int, TestError> = Result.Error(TestError.SOMETHING)
        val mapped = result.map { it * 2 }
        assertIs<Result.Error<TestError>>(mapped)
        assertEquals(TestError.SOMETHING, mapped.error)
    }

    @Test
    fun onSuccessExecutesOnSuccess() {
        var captured: Int? = null
        val result: Result<Int, TestError> = Result.Success(42)
        result.onSuccess { captured = it }
        assertEquals(42, captured)
    }

    @Test
    fun onSuccessDoesNotExecuteOnError() {
        var captured: Int? = null
        val result: Result<Int, TestError> = Result.Error(TestError.SOMETHING)
        result.onSuccess { captured = it }
        assertEquals(null, captured)
    }

    @Test
    fun onFailureExecutesOnError() {
        var captured: TestError? = null
        val result: Result<Int, TestError> = Result.Error(TestError.SOMETHING)
        result.onFailure { captured = it }
        assertEquals(TestError.SOMETHING, captured)
    }

    @Test
    fun onFailureDoesNotExecuteOnSuccess() {
        var captured: TestError? = null
        val result: Result<Int, TestError> = Result.Success(1)
        result.onFailure { captured = it }
        assertEquals(null, captured)
    }

    @Test
    fun asEmptyResultMapsToUnit() {
        val result: Result<Int, TestError> = Result.Success(5)
        val empty = result.asEmptyResult()
        assertIs<Result.Success<Unit>>(empty)
    }

    @Test
    fun asEmptyResultPreservesError() {
        val result: Result<Int, TestError> = Result.Error(TestError.SOMETHING)
        val empty = result.asEmptyResult()
        assertIs<Result.Error<TestError>>(empty)
        assertEquals(TestError.SOMETHING, empty.error)
    }

    private enum class TestError : Error {
        SOMETHING
    }
}
