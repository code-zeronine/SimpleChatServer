import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension
import kotlin.time.Duration.Companion.minutes

/**
 * Kotest 프로젝트 전역 설정
 * 
 * 모든 테스트에 공통으로 적용되는 설정을 정의합니다.
 */
class ProjectConfig : AbstractProjectConfig() {
    
    /**
     * 전역적으로 적용할 Extension들
     */
    override fun extensions() = listOf(
        SpringExtension
    )
    
    /**
     * 테스트 코루틴 디스패처 활성화
     */
    override var testCoroutineDispatcher = true
    
    /**
     * 테스트 실행 순서 - 결정적 실행을 위해 설정
     */
    override var globalAssertSoftly = false
    
    /**
     * 테스트 타임아웃 설정
     */
    override var timeout = 1.minutes
    
    /**
     * 병렬 테스트 실행 설정
     */
    override var parallelism = 1
}