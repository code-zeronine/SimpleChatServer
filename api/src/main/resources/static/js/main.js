/**
 * SimpleChatServer - Main JavaScript
 * 
 * 공통 유틸리티 함수와 전역 기능을 제공합니다.
 *
 * Note: The handleLeaveRoom function and its related response processing are located in rooms.js,
 * not in this file (main.js).
 */

// 전역 설정
window.SimpleChatServer = {
    apiUrl: '/api',
    wsUrl: `ws://${window.location.host}/ws/chat`,
    version: '1.0.0',
    utils: {} // 유틸리티 객체 초기화
};

/**
 * DOM 유틸리티
 */
const DOM = {
    select: (selector, parent = document) => parent.querySelector(selector),
    selectAll: (selector, parent = document) => parent.querySelectorAll(selector),
    create: (tag, attributes = {}, content = '') => {
        const element = document.createElement(tag);
        Object.entries(attributes).forEach(([key, value]) => {
            if (key === 'className') element.className = value;
            else if (key === 'dataset') Object.entries(value).forEach(([dataKey, dataValue]) => element.dataset[dataKey] = dataValue);
            else element.setAttribute(key, value);
        });
        if (content) element.innerHTML = content;
        return element;
    },
    addClass: (element, className) => element?.classList.add(className),
    removeClass: (element, className) => element?.classList.remove(className),
    toggleClass: (element, className) => element?.classList.toggle(className),
    hasClass: (element, className) => element?.classList.contains(className),
    on: (element, event, handler, options = {}) => element?.addEventListener(event, handler, options),
    off: (element, event, handler) => element?.removeEventListener(event, handler),
    setStyle: (element, styles) => {
        if (element && typeof styles === 'object') {
            Object.entries(styles).forEach(([property, value]) => element.style[property] = value);
        }
    }
};

/**
 * HTTP 요청 유틸리티
 */
const Http = {
    request: async (url, options = {}) => {
        const token = Storage.getToken();
        const defaultHeaders = { 'Content-Type': 'application/json' };
        if (token) defaultHeaders['Authorization'] = `Bearer ${token}`;
        const config = { headers: { ...defaultHeaders, ...options.headers }, ...options };
        try {
            const response = await fetch(url, config);

            if (response.status === 204) return { success: true, data: null }; // No Content
            
            // Check if response has content and is JSON
            const contentType = response.headers.get('Content-Type') || '';
            const hasJsonContent = contentType.includes('application/json');
            
            let data = null;
            if (hasJsonContent) {
                try {
                    const text = await response.text();
                    data = text ? JSON.parse(text) : { success: true };
                } catch (jsonError) {
                    console.warn('Failed to parse JSON response:', jsonError);
                    data = { success: response.ok };
                }
            } else {
                // Non-JSON response, assume success if status is ok
                data = { success: response.ok };
            }
            
            if (!response.ok) throw new HttpError(response.status, data.message || '요청 처리 중 오류가 발생했습니다.', data.errors);
            return data;
        } catch (error) {
            if (error instanceof HttpError) throw error;
            console.error('Network error details:', error);
            throw new HttpError(500, '네트워크 오류가 발생했습니다.');
        }
    },
    get: (url, params = {}) => {
        const urlObj = new URL(url, window.location.origin);
        Object.entries(params).forEach(([key, value]) => {
            if (value !== null && value !== undefined) urlObj.searchParams.append(key, value);
        });
        return Http.request(urlObj.toString(), { method: 'GET' });
    },
    post: (url, data = {}) => Http.request(url, { method: 'POST', body: JSON.stringify(data) }),
    put: (url, data = {}) => Http.request(url, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (url) => Http.request(url, { method: 'DELETE' })
};

class HttpError extends Error {
    constructor(status, message, errors = null) {
        super(message);
        this.name = 'HttpError';
        this.status = status;
        this.errors = errors;
    }
}

/**
 * 스토리지 유틸리티
 */
const Storage = {
    setToken: (token) => localStorage.setItem('auth_token', token),
    getToken: () => localStorage.getItem('auth_token'),
    removeToken: () => localStorage.removeItem('auth_token'),
    setUser: (user) => localStorage.setItem('user_info', JSON.stringify(user)),
    getUser: () => {
        const userInfo = localStorage.getItem('user_info');
        return userInfo ? JSON.parse(userInfo) : null;
    },
    removeUser: () => localStorage.removeItem('user_info'),
    set: (key, value) => localStorage.setItem(key, JSON.stringify(value)),
    get: (key, defaultValue = null) => {
        const item = localStorage.getItem(key);
        try {
            return item ? JSON.parse(item) : defaultValue;
        } catch (e) {
            return defaultValue;
        }
    },
    clear: () => localStorage.clear()
};

/**
 * 폼 유틸리티
 */
const Form = {
    getData: (formElement) => {
        const formData = new FormData(formElement);
        const data = {};
        for (const [key, value] of formData.entries()) {
            data[key] = value;
        }
        return data;
    },
    validate: (formElement) => {
        // Basic validation, can be expanded
        const inputs = formElement.querySelectorAll('[required]');
        let isValid = true;
        inputs.forEach(input => {
            if (!input.value.trim()) isValid = false;
        });
        return { isValid, errors: {} }; // Simplified
    },
    showErrors: (formElement, errors) => {
        Form.clearErrors(formElement);
        Object.entries(errors).forEach(([field, message]) => {
            const errorElement = DOM.select(`#${field}Error`, formElement);
            if (errorElement) {
                errorElement.textContent = message;
                errorElement.style.display = 'block';
            }
        });
    },
    showSuccess: (formElement, message) => {
        const successElement = DOM.select('.form-success', formElement);
        if (successElement) {
            successElement.textContent = message;
            successElement.style.display = 'block';
            setTimeout(() => {
                successElement.style.display = 'none';
                successElement.textContent = '';
            }, 5000);
        }
    },
    clearErrors: (formElement) => {
        const errorElements = formElement.querySelectorAll('.form-error');
        errorElements.forEach(el => el.style.display = 'none');
    },
    reset: (formElement) => formElement.reset()
};

/**
 * 로딩 상태 관리
 */
const Loading = {
    showButton: (button) => {
        const btn = typeof button === 'string' ? DOM.select(button) : button;
        if (!btn) return;
        btn.disabled = true;
        const textEl = btn.querySelector('.btn-text');
        const loadingEl = btn.querySelector('.btn-loading');
        if (textEl) textEl.style.display = 'none';
        if (loadingEl) loadingEl.style.display = 'inline-flex';
    },
    hideButton: (button) => {
        const btn = typeof button === 'string' ? DOM.select(button) : button;
        if (!btn) return;
        btn.disabled = false;
        const textEl = btn.querySelector('.btn-text');
        const loadingEl = btn.querySelector('.btn-loading');
        if (textEl) textEl.style.display = 'inline-block';
        if (loadingEl) loadingEl.style.display = 'none';
    }
};

/**
 * 토스트 알림
 */
const Toast = {
    show: (message, type = 'info', duration = 3000) => {
        let container = DOM.select('.toast-container');
        if (!container) {
            container = DOM.create('div', { className: 'toast-container' });
            document.body.appendChild(container);
        }
        const iconMap = { success: '<i class="fas fa-check-circle"></i>', error: '<i class="fas fa-times-circle"></i>', warning: '<i class="fas fa-exclamation-triangle"></i>', info: '<i class="fas fa-info-circle"></i>' };
        const toast = DOM.create('div', { className: `toast toast-${type}` }, `
            <div class="toast-content">
                <span class="toast-icon">${iconMap[type]}</span>
                <span class="toast-message">${message}</span>
            </div>
            <button class="toast-close"><i class="fas fa-times"></i></button>
        `);
        container.prepend(toast);
        const close = () => {
            toast.classList.add('fade-out');
            toast.addEventListener('animationend', () => toast.remove());
        };
        DOM.on(toast.querySelector('.toast-close'), 'click', close);
        if (duration > 0) setTimeout(close, duration);
    },
    success: (message, duration) => Toast.show(message, 'success', duration),
    error: (message, duration) => Toast.show(message, 'error', duration),
    warning: (message, duration) => Toast.show(message, 'warning', duration),
    info: (message, duration) => Toast.show(message, 'info', duration)
};

/**
 * 기타 유틸리티
 */
const Utils = {
    debounce: (func, wait) => {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },
    throttle: (func, limit) => {
        let inThrottle;
        return function(...args) {
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    },
    formatDate: (date, format = 'YYYY-MM-DD HH:mm') => {
        const d = new Date(date);
        if (isNaN(d.getTime())) {
            console.warn('formatDate: Invalid date:', date);
            return '';
        }
        
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        
        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes);
    },
    formatRelativeTime: (date) => {
        const now = new Date();
        const diff = now - new Date(date);
        const seconds = Math.floor(diff / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        if (days > 0) return `${days}일 전`;
        if (hours > 0) return `${hours}시간 전`;
        if (minutes > 0) return `${minutes}분 전`;
        return '방금 전';
    },
    escapeHtml: (str) => {
        return str
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    },
    parseUrlParams: () => Object.fromEntries(new URLSearchParams(window.location.search))
};

/**
 * 페이지 라우팅
 */
const Router = {
    navigate: (path) => { window.location.href = path; },
    back: () => { window.history.back(); }
};

/**
 * 테마 관리
 */
const ThemeManager = {
    init() {
        this.themeToggleBtn = DOM.select('#themeToggleBtn');
        this.currentTheme = Storage.get('theme') || this.getSystemTheme();
        this.applyTheme(this.currentTheme);
        if (this.themeToggleBtn) {
            DOM.on(this.themeToggleBtn, 'click', () => {
                const newTheme = this.currentTheme === 'light' ? 'dark' : 'light';
                this.applyTheme(newTheme);
                Storage.set('theme', newTheme);
            });
        }
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
            if (!Storage.get('theme')) {
                this.applyTheme(e.matches ? 'dark' : 'light');
            }
        });
    },
    applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        this.currentTheme = theme;
        if (this.themeToggleBtn) {
            const icon = this.themeToggleBtn.querySelector('.theme-icon');
            if(icon) icon.textContent = theme === 'light' ? '🌙' : '☀️';
        }
    },
    getSystemTheme: () => window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
};

/**
 * 인증 관련 유틸리티
 */
const Auth = {
    isAuthenticated: () => !!Storage.getToken(),
    logout: () => {
        Storage.clear();
        Router.navigate('/login?logout=success');
    },
    requireAuth: () => {
        if (!Auth.isAuthenticated()) {
            Router.navigate('/login?auth=required');
            return false;
        }
        return true;
    }
};

/**
 * WebSocket 클라이언트 유틸리티
 */
const WebSocketUtil = {
    getClient: (options = {}) => {
        if (typeof window.WebSocketClient !== 'undefined') {
            return window.WebSocketClient.getGlobalClient({ debug: window.location.hostname === 'localhost', ...options });
        }
        return null;
    }
};

// 전역 유틸리티 객체 노출
window.SimpleChatServer.utils = {
    DOM,
    Http,
    Storage,
    Form,
    Loading,
    Toast,
    Utils,
    Router,
    ThemeManager,
    Auth,
    WebSocketUtil
};

// DOM 로드 완료 후 실행
document.addEventListener('DOMContentLoaded', () => {
    ThemeManager.init();
    console.log('SimpleChatServer main.js loaded and initialized.');
});
