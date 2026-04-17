/**
 * API 请求封装模块
 * 统一管理所有后端接口请求
 */

const API_CONFIG = {
    // API 基础地址，支持环境变量配置
    baseURL: window.API_BASE_URL || '/api',
    timeout: 30000
};

// 通用请求函数
function request(url, options = {}) {
    const defaultOptions = {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include'
    };

    const config = { ...defaultOptions, ...options };

    // 合并 headers
    if (options.headers) {
        config.headers = { ...defaultOptions.headers, ...options.headers };
    }

    return fetch(`${API_CONFIG.baseURL}${url}`, config)
        .then(response => {
            return response.text().then(text => {
                let data = {};
                try {
                    data = text ? JSON.parse(text) : {};
                } catch (e) {
                    data = { rawText: text };
                }

                if (!response.ok) {
                    const error = new Error(data.message || data.msg || `HTTP ${response.status}`);
                    error.code = data.code || response.status;
                    error.data = data;
                    throw error;
                }

                if (data.code !== undefined && data.code !== 200) {
                    const error = new Error(data.message || '请求失败');
                    error.code = data.code;
                    error.data = data;
                    throw error;
                }

                return data;
            });
        });
}

// API 对象
const API = {
    // 认证相关
    auth: {
        // 检查登录状态
        check: () => request('/auth/check'),

        // 登录
        login: (data) => request('/auth/login', {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 登出
        logout: () => request('/auth/logout', {
            method: 'POST'
        }),

        // 获取用户列表
        getUsers: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/auth/users?${query}`);
        },

        // 创建用户
        createUser: (data) => request('/auth/users', {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 更新用户
        updateUser: (id, data) => request(`/auth/users/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),

        // 删除用户
        deleteUser: (id) => request(`/auth/users/${id}`, {
            method: 'DELETE'
        })
    },

    // 接口配置
    config: {
        // 分页查询接口列表
        list: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/config/page?${query}`);
        },

        // 获取接口详情
        get: (id) => request(`/config/${id}`),

        // 创建接口
        create: (data) => request('/config', {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 更新接口
        update: (id, data) => request(`/config/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),

        // 删除接口
        delete: (id) => request(`/config/${id}`, {
            method: 'DELETE'
        }),

        // 切换接口状态
        toggle: (id) => request(`/config/${id}/toggle`, {
            method: 'POST'
        }),

        // Curl 导入
        importCurl: (curl) => request('/config/import/curl', {
            method: 'POST',
            body: JSON.stringify({ curl })
        }),

        // 获取所有启用的接口
        getActiveList: () => request('/config/active'),

        // 根据编码获取接口
        getByCode: (code) => request(`/config/code/${code}`)
    },

    // 调用日志
    invoke: {
        // 调试接口
        debug: (data) => request('/invoke', {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 获取日志列表
        getLogs: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/invoke/logs?${query}`);
        },

        // 获取日志详情
        getLogDetail: (id) => request(`/invoke/logs/detail/${id}`),

        // 批量删除日志
        deleteLogs: (ids) => request(`/invoke/logs`, {
            method: 'DELETE',
            body: JSON.stringify(ids)
        })
    },

    // 环境配置
    environment: {
        // 获取环境列表（分页）
        list: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/environment/list?${query}`);
        },

        // 获取环境详情
        get: (id) => request(`/environment/${id}`),

        // 创建环境
        create: (data) => request(`/environment`, {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 更新环境
        update: (id, data) => request(`/environment/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),

        // 删除环境
        delete: (id) => request(`/environment/${id}`, {
            method: 'DELETE'
        }),

        // 获取所有启用的系统名称
        getSystems: () => request(`/environment/systems`),

        // 获取指定系统下的环境列表
        getBySystem: (systemName) => request(`/environment/by-system/${systemName}`)
    }
};

// 全局导出
if (typeof window !== 'undefined') {
    window.API = API;
    window.API_CONFIG = API_CONFIG;
}