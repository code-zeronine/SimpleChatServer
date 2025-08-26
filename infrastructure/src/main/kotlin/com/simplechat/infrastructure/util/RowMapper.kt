package com.simplechat.infrastructure.util

import io.r2dbc.spi.Row
import java.time.LocalDateTime

/**
 * R2DBC Row 매핑을 위한 타입 안전 유틸리티
 * 
 * 데이터베이스 행을 객체로 변환할 때 타입 안전성과 null 처리를 개선합니다.
 */
object RowMapper {

    /**
     * 필수 컬럼 값을 안전하게 추출합니다.
     * null인 경우 예외를 발생시킵니다.
     */
    inline fun <reified T> Row.getRequired(column: String): T {
        return get(column, T::class.java) 
            ?: throw IllegalStateException("Required column '$column' is null")
    }

    /**
     * 선택적 컬럼 값을 안전하게 추출합니다.
     * null인 경우 null을 반환합니다.
     */
    inline fun <reified T> Row.getOptional(column: String): T? {
        return get(column, T::class.java)
    }

    /**
     * 문자열 컬럼을 안전하게 추출합니다.
     */
    fun Row.getString(column: String): String {
        return getRequired<String>(column)
    }

    /**
     * 선택적 문자열 컬럼을 안전하게 추출합니다.
     */
    fun Row.getStringOrNull(column: String): String? {
        return getOptional<String>(column)
    }

    /**
     * Long 컬럼을 안전하게 추출합니다.
     */
    fun Row.getLong(column: String): Long {
        return getRequired<Long>(column)
    }

    /**
     * 선택적 Long 컬럼을 안전하게 추출합니다.
     */
    fun Row.getLongOrNull(column: String): Long? {
        return getOptional<Long>(column)
    }

    /**
     * Integer 컬럼을 안전하게 추출합니다.
     */
    fun Row.getInt(column: String): Int {
        return getRequired<Int>(column)
    }

    /**
     * 선택적 Integer 컬럼을 안전하게 추출합니다.
     */
    fun Row.getIntOrNull(column: String): Int? {
        return getOptional<Int>(column)
    }

    /**
     * Boolean 컬럼을 안전하게 추출합니다.
     */
    fun Row.getBoolean(column: String): Boolean {
        return getRequired<Boolean>(column)
    }

    /**
     * 선택적 Boolean 컬럼을 안전하게 추출합니다.
     */
    fun Row.getBooleanOrNull(column: String): Boolean? {
        return getOptional<Boolean>(column)
    }

    /**
     * LocalDateTime 컬럼을 안전하게 추출합니다.
     */
    fun Row.getLocalDateTime(column: String): LocalDateTime {
        return getRequired<LocalDateTime>(column)
    }

    /**
     * 선택적 LocalDateTime 컬럼을 안전하게 추출합니다.
     */
    fun Row.getLocalDateTimeOrNull(column: String): LocalDateTime? {
        return getOptional<LocalDateTime>(column)
    }

    /**
     * Row 매핑 함수를 안전하게 실행합니다.
     * 매핑 과정에서 발생하는 예외를 포착하여 의미있는 에러 메시지를 제공합니다.
     */
    fun <T> mapRowSafely(row: Row, mapper: (Row) -> T): T {
        return try {
            mapper(row)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to map row to object: ${e.message}", e)
        }
    }

    /**
     * 컬럼이 존재하는지 확인합니다.
     */
    fun Row.hasColumn(column: String): Boolean {
        return try {
            get(column) != null
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 모든 컬럼 메타데이터를 가져옵니다. (디버깅용)
     */
    fun Row.getColumnMetadata(): Map<String, Any?> {
        return metadata.columnMetadatas.associate { columnMetadata ->
            val name = columnMetadata.name
            val value = try {
                get(name)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
            name to value
        }
    }
}