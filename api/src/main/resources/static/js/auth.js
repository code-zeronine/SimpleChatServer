/**
 * SimpleChatServer - Authentication JavaScript
 * 
 * 로그인, 회원가입 등 인증 관련 기능을 담당합니다.
 */

document.addEventListener('DOMContentLoaded', function() {
    const { DOM, Form, Http, Storage, Loading, Toast, Router } = window.SimpleChatServer.utils;
    
    // 이미 로그인된 경우 리다이렉트
    if (window.SimpleChatServer.utils.Auth.isAuthenticated()) {
        Router.navigate('/chat');
        return;
    }
    
    // 폼 설정
    setupLoginForm();
    setupDemoAccounts();
    
    // URL 파라미터 확인 (로그아웃 메시지 등)
    handleUrlParams();
});

/**
 * 로그인 폼 설정
 */
function setupLoginForm() {
    const { DOM, Form, Http, Storage, Loading, Toast, Router } = window.SimpleChatServer.utils;
    const loginForm = DOM.select('#loginForm');
    
    if (!loginForm) return;
    
    DOM.on(loginForm, 'submit', async function(e) {
        e.preventDefault();
        
        const submitBtn = DOM.select('#loginButton');
        const formData = Form.getData(this);
        
        // 폼 유효성 검사
        const validation = Form.validate(this);
        if (!validation.isValid) {
            Form.showErrors(this, validation.errors);
            return;
        }
        
        // 로딩 상태 표시
        Loading.showButton(submitBtn);
        Form.clearErrors(this);
        
        try {
            // 로그인 API 호출
            const response = await Http.post('/api/auth/login', {
                email: formData.email,
                password: formData.password
            });
            
            // 토큰 저장
            Storage.setToken(response.accessToken);
            
            // 사용자 정보 조회 및 저장
            const userInfo = await Http.get('/api/auth/me');
            Storage.setUser(userInfo);
            
            // 로그인 상태 유지 설정
            if (formData.rememberMe) {
                Storage.set('remember_login', true);
            }
            
            // 성공 메시지 및 리다이렉트
            Form.showSuccess(this, '로그인 성공! 채팅 페이지로 이동합니다.');
            Toast.success('로그인되었습니다.');
            
            setTimeout(() => {
                Router.navigate('/chat');
            }, 1000);
            
        } catch (error) {
            // 에러 처리
            let errorMessage = '로그인에 실패했습니다.';
            
            if (error.status === 401) {
                errorMessage = '이메일 또는 비밀번호가 올바르지 않습니다.';
            } else if (error.status === 429) {
                errorMessage = '너무 많은 시도가 있었습니다. 잠시 후 다시 시도해주세요.';
            } else if (error.status === 500) {
                errorMessage = '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
            }
            
            const formError = DOM.select('#formError');
            if (formError) {
                formError.textContent = errorMessage;
                DOM.addClass(formError, 'show');
            }
            
            Toast.error(errorMessage);
            
        } finally {
            Loading.hideButton(submitBtn);
        }
    });
    
    // 실시간 유효성 검사
    setupRealTimeValidation(loginForm);
}

/**
 * 실시간 유효성 검사 설정
 */
function setupRealTimeValidation(form) {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    const inputs = form.querySelectorAll('input[required]');
    
    inputs.forEach(input => {
        const debouncedValidation = Utils.debounce(() => {
            validateSingleField(input);
        }, 500);
        
        DOM.on(input, 'input', debouncedValidation);
        DOM.on(input, 'blur', () => validateSingleField(input));
    });
}

/**
 * 개별 필드 유효성 검사
 */
function validateSingleField(input) {
    const { DOM } = window.SimpleChatServer.utils;
    const value = input.value.trim();
    const name = input.name;
    const errorElement = DOM.select(`#${name}Error`);
    
    let isValid = true;
    let errorMessage = '';
    
    // 필수 필드 검사
    if (input.hasAttribute('required') && !value) {
        isValid = false;
        errorMessage = '이 필드는 필수입니다.';
    }
    // 이메일 형식 검사
    else if (input.type === 'email' && value) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(value)) {
            isValid = false;
            errorMessage = '올바른 이메일 형식이 아닙니다.';
        }
    }
    // 비밀번호 길이 검사
    else if (input.type === 'password' && value && value.length < 6) {
        isValid = false;
        errorMessage = '비밀번호는 최소 6자 이상이어야 합니다.';
    }
    
    // UI 업데이트
    if (isValid) {
        DOM.removeClass(input, 'error');
        if (errorElement) {
            errorElement.textContent = '';
            DOM.removeClass(errorElement, 'show');
        }
    } else {
        DOM.addClass(input, 'error');
        if (errorElement) {
            errorElement.textContent = errorMessage;
            DOM.addClass(errorElement, 'show');
        }
    }
    
    return isValid;
}

/**
 * 데모 계정 설정
 */
function setupDemoAccounts() {
    const { DOM } = window.SimpleChatServer.utils;
    const demoButtons = DOM.selectAll('.demo-login');
    
    demoButtons.forEach(button => {
        DOM.on(button, 'click', function(e) {
            e.preventDefault();
            const account = this.closest('.demo-account');
            
            if (account) {
                const email = account.dataset.email;
                const password = account.dataset.password;
                
                // 폼 필드 자동 입력
                const emailInput = DOM.select('#email');
                const passwordInput = DOM.select('#password');
                
                if (emailInput && passwordInput) {
                    emailInput.value = email;
                    passwordInput.value = password;
                    
                    // 입력 애니메이션
                    emailInput.focus();
                    setTimeout(() => {
                        passwordInput.focus();
                        setTimeout(() => {
                            // 로그인 폼 제출
                            const loginForm = DOM.select('#loginForm');
                            if (loginForm) {
                                const submitEvent = new Event('submit', {
                                    bubbles: true,
                                    cancelable: true
                                });
                                loginForm.dispatchEvent(submitEvent);
                            }
                        }, 300);
                    }, 300);
                }
            }
        });
        
        // 데모 계정 호버 효과
        const account = button.closest('.demo-account');
        DOM.on(account, 'mouseenter', function() {
            this.style.transform = 'translateY(-2px)';
            this.style.boxShadow = '0 4px 12px rgba(0,123,255,0.15)';
        });
        
        DOM.on(account, 'mouseleave', function() {
            this.style.transform = 'translateY(0)';
            this.style.boxShadow = 'none';
        });
    });
}

/**
 * URL 파라미터 처리
 */
function handleUrlParams() {
    const { Utils, Toast } = window.SimpleChatServer.utils;
    const params = Utils.parseUrlParams();
    
    // 로그아웃 메시지
    if (params.logout === 'success') {
        Toast.info('로그아웃되었습니다.');
    }
    
    // 세션 만료 메시지
    if (params.expired === 'true') {
        Toast.warning('세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    
    // 인증 필요 메시지
    if (params.auth === 'required') {
        Toast.info('로그인이 필요한 페이지입니다.');
    }
}

/**
 * 회원가입 페이지용 함수들 (register.html에서 사용)
 */
window.SimpleChatServer.register = {
    /**
     * 회원가입 폼 설정
     */
    setupRegisterForm: function() {
        const { DOM, Form, Http, Loading, Toast, Router } = window.SimpleChatServer.utils;
        const registerForm = DOM.select('#registerForm');
        
        if (!registerForm) return;
        
        DOM.on(registerForm, 'submit', async function(e) {
            e.preventDefault();
            
            const submitBtn = DOM.select('#registerButton');
            const formData = Form.getData(this);
            
            // 폼 유효성 검사
            const validation = validateRegisterForm(formData);
            if (!validation.isValid) {
                Form.showErrors(this, validation.errors);
                return;
            }
            
            // 로딩 상태 표시
            Loading.showButton(submitBtn);
            Form.clearErrors(this);
            
            try {
                // 회원가입 API 호출
                await Http.post('/api/auth/register', {
                    name: formData.name,
                    email: formData.email,
                    password: formData.password
                });
                
                // 성공 메시지
                Form.showSuccess(this, '회원가입이 완료되었습니다! 로그인 페이지로 이동합니다.');
                Toast.success('회원가입이 완료되었습니다.');
                
                setTimeout(() => {
                    Router.navigate('/login');
                }, 2000);
                
            } catch (error) {
                let errorMessage = '회원가입에 실패했습니다.';
                
                if (error.status === 409) {
                    errorMessage = '이미 존재하는 이메일입니다.';
                } else if (error.status === 400) {
                    errorMessage = '입력 정보를 확인해주세요.';
                }
                
                const formError = DOM.select('#formError');
                if (formError) {
                    formError.textContent = errorMessage;
                    DOM.addClass(formError, 'show');
                }
                
                Toast.error(errorMessage);
                
            } finally {
                Loading.hideButton(submitBtn);
            }
        });
        
        // 실시간 유효성 검사
        setupRealTimeValidation(registerForm);
    },
    
    /**
     * 회원가입 폼 유효성 검사
     */
    validateRegisterForm: function(data) {
        const errors = {};
        
        // 이름 검사
        if (!data.name || data.name.trim().length < 2) {
            errors.name = '이름은 최소 2자 이상이어야 합니다.';
        }
        
        // 이메일 검사
        if (!data.email) {
            errors.email = '이메일은 필수입니다.';
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
            errors.email = '올바른 이메일 형식이 아닙니다.';
        }
        
        // 비밀번호 검사
        if (!data.password) {
            errors.password = '비밀번호는 필수입니다.';
        } else if (data.password.length < 6) {
            errors.password = '비밀번호는 최소 6자 이상이어야 합니다.';
        } else if (!/(?=.*[a-zA-Z])(?=.*\d)/.test(data.password)) {
            errors.password = '비밀번호는 영문과 숫자를 포함해야 합니다.';
        }
        
        // 비밀번호 확인
        if (data.password !== data.confirmPassword) {
            errors.confirmPassword = '비밀번호가 일치하지 않습니다.';
        }
        
        // 이용약관 동의
        if (!data.agreeTerms) {
            errors.agreeTerms = '이용약관에 동의해주세요.';
        }
        
        return {
            isValid: Object.keys(errors).length === 0,
            errors
        };
    }
};

// 비밀번호 표시/숨김 토글 기능
function setupPasswordToggle() {
    const { DOM } = window.SimpleChatServer.utils;
    const passwordInputs = DOM.selectAll('input[type="password"]');
    
    passwordInputs.forEach(input => {
        const toggleBtn = DOM.create('button', {
            type: 'button',
            className: 'password-toggle',
            style: 'position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: none; cursor: pointer;'
        }, '👁️');
        
        // 상대 위치를 위한 wrapper 추가
        const wrapper = DOM.create('div', { style: 'position: relative;' });
        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);
        wrapper.appendChild(toggleBtn);
        
        DOM.on(toggleBtn, 'click', function() {
            if (input.type === 'password') {
                input.type = 'text';
                this.textContent = '🙈';
            } else {
                input.type = 'password';
                this.textContent = '👁️';
            }
        });
    });
}

// 페이지 로드 완료 후 추가 기능 초기화
setTimeout(() => {
    setupPasswordToggle();
}, 100);

console.log('SimpleChatServer auth.js loaded successfully');