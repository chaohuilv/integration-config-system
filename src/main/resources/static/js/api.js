/**
 * API 请求封装模块 - Bearer Token 认证模式
 * 统一管理所有后端接口请求
 */

const API_CONFIG = {
    // API 基础地址，支持环境变量配置
    baseURL: window.API_BASE_URL || '/api',
    timeout: 30000
};

// 存储 access_token
const TOKEN_KEY = 'integration_access_token';

// 获取存储的 token
function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

// 存储 token
function setToken(token) {
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
    } else {
        localStorage.removeItem(TOKEN_KEY);
    }
}

// 通用请求函数
function request(url, options = {}) {
    const defaultOptions = {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    };

    const config = { ...defaultOptions, ...options };

    // 合并 headers
    if (options.headers) {
        config.headers = { ...defaultOptions.headers, ...options.headers };
    }

    // 添加 Authorization: Bearer <token> 头
    const token = getToken();
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
    }

    return fetch(`${API_CONFIG.baseURL}${url}`, config)
        .then(response => {
            return response.text().then(text => {
                let data = {};
                try {
                    data = text ? JSON.parse(text) : {};
                } catch (e) {
                    // 非 200 状态且 body 不是 JSON → 包装成错误
                    if (!response.ok) {
                        const err = new Error(`HTTP ${response.status}: ${text.substring(0, 200)}`);
                        err.code = response.status;
                        throw err;
                    }
                    data = { rawText: text };
                }

                if (!response.ok) {
                    const error = new Error(data.message || data.msg || `HTTP ${response.status}`);
                    error.code = data.code || response.status;
                    error.data = data;
                    throw error;
                }

                if (data.code !== undefined && data.code !== 200) {
                    // 业务返回未登录码
                    if ((data.code === 401 || data.code === 403) && !url.includes('/auth/')) {
                        setToken(null);
                        setTimeout(() => { window.top.location.href = '/login.html'; }, 100);
                    }
                    const error = new Error(data.message || '请求失败');
                    error.code = data.code;
                    error.data = data;
                    throw error;
                }

                return data;
            });
        }).catch(err => {
            // fetch 本身失败（网络错误）或内层抛出的错误
            throw err;
        });
}

// API 对象
const API = {
    // 认证相关
    auth: {
        // 检查登录状态
        check: () => request('/auth/check'),

        // 登录
        login: async (data) => {
            const result = await request('/auth/login', {
                method: 'POST',
                body: JSON.stringify(data)
            });
            // 登录成功后保存 token
            if (result.code === 200 && result.data && result.data.access_token) {
                setToken(result.data.access_token);
            }
            return result;
        },

        // 登出
        logout: async () => {
            try {
                await request('/auth/logout', {
                    method: 'POST'
                });
            } finally {
                // 无论后端是否成功，都清除本地 token
                setToken(null);
            }
        },

        // 获取用户列表
        getUsers: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/auth/users?${query}`);
        },

        // 获取所有用户（下拉选择用）
        listAll: () => request('/auth/users/all'),

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
        }),

        // 获取当前用户的菜单
        getMenus: () => request('/auth/menus'),

        // 获取当前用户的权限
        getPermissions: () => request('/auth/permissions')
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
        getByCode: (code) => request(`/config/code/${code}`),

        // ========== 版本控制 ==========
        // 创建新版本（基于现有接口）
        createVersion: (sourceId, data) => request(`/config/${sourceId}/version`, {
            method: 'POST',
            body: JSON.stringify(data)
        }),
        // 获取某接口的所有版本
        getVersions: (id) => request(`/config/${id}/versions`),
        // 设置某版本为最新推荐版本
        setLatest: (id) => request(`/config/${id}/set-latest`, { method: 'POST' }),
        toggleDeprecated: (id) => request(`/config/${id}/deprecate`, { method: 'POST' }),
        // 废弃/恢复版本
        toggleDeprecated: (id) => request(`/config/${id}/deprecate`, { method: 'POST' })
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
    },

    // 审计日志
    auditLog: {
        // 分页查询
        list: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/audit-log/list?${query}`);
        },
        // 批量删除
        batchDelete: (ids) => request(`/audit-log/batch-delete`, {
            method: 'POST',
            body: JSON.stringify({ ids })
        }),
        // 导出
        export: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/audit-log/export?${query}`);
        },
        // 详情
        get: (id) => request(`/audit-log/${id}`)
    },

    // OpenAPI 导入导出
    openapi: {
        // 解析 JSON，返回预览列表
        parse: (json) => request('/openapi/parse', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ json })
        }),
        // 上传文件解析
        upload: (formData) => fetch(baseUrl + '/openapi/upload', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + getToken() },
            body: formData
        }).then(r => r.json()),
        // 批量导入
        batchImport: (dtos) => request('/openapi/import', {
            method: 'POST',
            body: JSON.stringify(dtos)
        }),
        // 导出全部（返回 JSON 字符串）
        exportAll: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request('/openapi/export' + (query ? '?' + query : ''));
        },
        // 批量导出（返回 JSON 字符串）
        exportSelected: (params = {}) => request('/openapi/export', {
            method: 'POST',
            body: JSON.stringify(params)
        })
    },

    // 角色权限管理
    roles: {
        // 获取所有角色
        list: () => request('/roles'),

        // 分页查询角色（含统计）
        page: (params) => request('/roles/page', { params }),

        // 获取启用的角色（下拉选择用）
        listActive: () => request('/roles/active'),

        // 获取单个角色
        get: (id) => request(`/roles/${id}`),

        // 创建角色
        create: (data) => request('/roles', {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        // 更新角色
        update: (id, data) => request(`/roles/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),

        // 删除角色
        delete: (id) => request(`/roles/${id}`, {
            method: 'DELETE'
        }),

        // 获取用户的角色ID列表
        getUserRoleIds: (userId) => request(`/roles/user/${userId}/ids`),

        // 获取角色下的用户ID列表
        getRoleUserIds: (roleId) => request(`/roles/${roleId}/users/ids`),

        // 设置角色的用户列表
        setRoleUsers: (roleId, userIds) => request(`/roles/${roleId}/users`, {
            method: 'POST',
            body: JSON.stringify({ userIds })
        }),

        // 设置用户的角色
        setUserRoles: (userId, roleIds) => request(`/roles/user/${userId}`, {
            method: 'POST',
            body: JSON.stringify({ roleIds })
        }),

        // 获取接口的角色ID列表
        getApiRoleIds: (apiId) => request(`/roles/api/${apiId}/ids`),

        // 获取角色的接口ID列表
        getRoleApiIds: (roleId) => request(`/roles/${roleId}/apis/ids`),

        // 设置角色的接口列表
        setRoleApis: (roleId, apiIds) => request(`/roles/${roleId}/apis`, {
            method: 'POST',
            body: JSON.stringify({ apiIds })
        }),

        // 设置接口的角色
        setApiRoles: (apiId, roleIds) => request(`/roles/api/${apiId}`, {
            method: 'POST',
            body: JSON.stringify({ roleIds })
        }),

        // 获取当前用户可访问的接口ID列表
        getAccessibleApis: () => request('/roles/accessible-apis'),

        // 获取角色的菜单ID列表
        getMenuIds: (roleId) => request(`/roles/${roleId}/menus`),

        // 设置角色的菜单
        setMenus: (roleId, menuIds) => request(`/roles/${roleId}/menus`, {
            method: 'POST',
            body: JSON.stringify({ menuIds })
        }),

        // 获取角色的权限ID列表
        getPermissionIds: (roleId) => request(`/roles/${roleId}/permissions`),

        // 设置角色的权限
        setPermissions: (roleId, permissionIds) => request(`/roles/${roleId}/permissions`, {
            method: 'POST',
            body: JSON.stringify({ permissionIds })
        }),

        // 给 ADMIN 角色分配所有权限
        assignAllPermissionsToAdmin: () => request('/roles/admin/assign-all-permissions', {
            method: 'POST'
        })
    },

    // 系统配置
    system: {
        // 获取所有菜单
        getMenus: () => request('/system/menus'),

        // 获取启用的菜单
        getActiveMenus: () => request('/system/menus/active'),

        // 按分组获取菜单
        getMenusGrouped: () => request('/system/menus/grouped'),

        // 获取所有权限
        getPermissions: () => request('/system/permissions'),

        // 获取按钮权限
        getButtonPermissions: () => request('/system/permissions/buttons')
    },

    // 实时大盘
    dashboard: {
        // 总览数据（卡片）
        getOverview: () => request('/dashboard/overview'),

        // 调用趋势（24小时）
        getInvokeTrend: (hours = 24) => request(`/dashboard/invoke-trend?hours=${hours}`),

        // 接口调用排行
        getTopApis: (limit = 10) => request(`/dashboard/top-apis?limit=${limit}`),

        // 审计活动分布
        getAuditStats: () => request('/dashboard/audit-stats'),

        // 最近活动流
        getRecentActivity: () => request('/dashboard/recent-activity'),

        // 系统健康状态
        getHealth: () => request('/dashboard/health'),

        // 系统硬件资源（CPU/内存/JVM）
        getSystemResources: () => request('/dashboard/system-resources')
    },
    // API 文档
    doc: {
        // 获取所有接口文档（按分组）
        list: () => request('/doc/list'),

        // 获取单个接口的完整文档
        detail: (id) => request('/doc/' + id),

        // 导出全部接口文档（Word）
        exportAll: function() {
            var token = localStorage.getItem('integration_access_token') || '';
            var url = API_CONFIG.baseURL + '/doc/export';
            var a = document.createElement('a');
            a.href = url;
            a.download = '';
            a.onclick = function() {
                var req = new XMLHttpRequest();
                req.open('GET', url, true);
                req.setRequestHeader('Authorization', 'Bearer ' + token);
                req.responseType = 'blob';
                req.onload = function() {
                    var blob = req.response;
                    var disposition = req.getResponseHeader('Content-Disposition');
                    var filename = '接口文档_' + new Date().toISOString().slice(0, 10) + '.docx';
                    if (disposition) {
                        var match = disposition.match(/filename\*?=['"]?(?:UTF-8'')?([^;\n"']+)/i);
                        if (match) filename = decodeURIComponent(match[1]);
                    }
                    var blobUrl = URL.createObjectURL(blob);
                    var link = document.createElement('a');
                    link.href = blobUrl;
                    link.download = filename;
                    link.click();
                    URL.revokeObjectURL(blobUrl);
                };
                req.send();
                return false;
            };
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
        },

        // 导出指定分组的接口文档（Word）
        exportGroup: function(groupName) {
            var token = localStorage.getItem('integration_access_token') || '';
            var url = API_CONFIG.baseURL + '/doc/export/group' + (groupName ? '?groupName=' + encodeURIComponent(groupName) : '');
            var req = new XMLHttpRequest();
            req.open('GET', url, true);
            req.setRequestHeader('Authorization', 'Bearer ' + token);
            req.responseType = 'blob';
            req.onload = function() {
                var blob = req.response;
                var disposition = req.getResponseHeader('Content-Disposition');
                var filename = '接口文档_' + (groupName || '全部') + '_' + new Date().toISOString().slice(0, 10) + '.docx';
                if (disposition) {
                    var match = disposition.match(/filename\*?=['"]?(?:UTF-8'')?([^;\n"']+)/i);
                    if (match) filename = decodeURIComponent(match[1]);
                }
                var blobUrl = URL.createObjectURL(blob);
                var link = document.createElement('a');
                link.href = blobUrl;
                link.download = filename;
                link.click();
                URL.revokeObjectURL(blobUrl);
            };
            req.send();
        }
    },

    // 告警管理
    alert: {
        // 分页查询告警规则
        pageRules: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/alert/rules?${query}`);
        },
        // 所有启用的规则
        getActiveRules: () => request('/alert/rules/active'),
        // 规则详情
        getRule: (id) => request(`/alert/rules/${id}`),
        // 创建规则
        createRule: (data) => request('/alert/rules', {
            method: 'POST',
            body: JSON.stringify(data)
        }),
        // 更新规则
        updateRule: (id, data) => request(`/alert/rules/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),
        // 删除规则
        deleteRule: (id) => request(`/alert/rules/${id}`, {
            method: 'DELETE'
        }),
        // 启用/停用
        toggleRule: (id) => request(`/alert/rules/${id}/toggle`, {
            method: 'POST'
        }),
        // 测试告警
        testAlert: (id) => request(`/alert/rules/${id}/test`, {
            method: 'POST'
        }),
        // 手动触发评估
        evaluateNow: (id) => request(`/alert/rules/${id}/evaluate`, {
            method: 'POST'
        }),
        // 告警概览
        overview: () => request('/alert/overview'),
        // 分页查询告警记录
        pageRecords: (params = {}) => {
            const query = new URLSearchParams(params).toString();
            return request(`/alert/records?${query}`);
        },
        // 确认告警
        acknowledge: (id) => request(`/alert/records/${id}/acknowledge`, {
            method: 'POST'
        }),
        // 标记已解决
        resolve: (id) => request(`/alert/records/${id}/resolve`, {
            method: 'POST'
        })
    }
};

// 全局导出
if (typeof window !== 'undefined') {
    window.API = API;
    window.API_CONFIG = API_CONFIG;
}
