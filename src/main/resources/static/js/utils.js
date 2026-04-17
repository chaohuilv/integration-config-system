/**
 * 通用工具函数库
 * 提供 Toast 提示、Confirm 弹窗、格式化等工具方法
 */

const Utils = (function() {
    'use strict';

    // ===== Toast 提示 =====
    function toast(msg, type = 'info', duration = 3000) {
        try {
            // 如果在 iframe 中，调用父窗口的 toast（防御：确保父窗口 toast 存在再调用）
            if (window.parent && window.parent !== window &&
                typeof window.parent.utils === 'object' &&
                typeof window.parent.utils.toast === 'function') {
                return window.parent.utils.toast(msg, type, duration);
            }

            const container = document.getElementById('toastContainer');
            if (!container) {
                console.warn('Toast container not found, creating one');
                // 兜底：自己创建一个
                const fallback = document.body || document.documentElement;
                if (fallback) {
                    const div = document.createElement('div');
                    div.style = 'position:fixed;top:20px;right:20px;z-index:99999;background:#333;color:#fff;padding:10px 16px;border-radius:6px;font-size:14px;';
                    div.textContent = `[${type}] ${msg}`;
                    fallback.appendChild(div);
                    setTimeout(() => div.remove(), duration);
                }
                return;
            }

            const icons = { success: '✅', error: '❌', info: '💡', warning: '⚠️' };
            const div = document.createElement('div');
            div.className = `toast ${type}`;
            div.innerHTML = `
                <span class="toast-icon">${icons[type] || '💡'}</span>
                <span class="toast-msg">${msg}</span>
                <button class="toast-close" onclick="this.parentElement.remove()">✕</button>
            `;
            container.appendChild(div);

            setTimeout(() => {
                div.classList.add('out');
                setTimeout(() => div.remove(), 220);
            }, duration);
        } catch (e) {
            console.error('[toast] error:', e);
            // 最后的兜底：直接 alert
            alert(`[${type}] ${msg}`);
        }
    }

    // ===== 确认弹窗 =====
    let _confirmResolve = null;

    function showConfirm(title, desc, icon = '⚠️', danger = false) {
        // 如果在 iframe 中，调用父窗口的 confirm
        if (window.parent && window.parent !== window && window.parent.utils && window.parent.utils.showConfirm) {
            return window.parent.utils.showConfirm(title, desc, icon, danger);
        }

        return new Promise(resolve => {
            _confirmResolve = resolve;
            const overlay = document.getElementById('confirmOverlay');
            if (!overlay) {
                console.warn('Confirm overlay not found');
                resolve(false);
                return;
            }

            document.getElementById('confirmIcon').textContent = icon;
            document.getElementById('confirmTitle').textContent = title;
            document.getElementById('confirmDesc').textContent = desc;
            const okBtn = document.getElementById('confirmOkBtn');
            okBtn.textContent = danger ? '确认删除' : '确定';
            okBtn.className = `btn ${danger ? 'btn-danger-ok' : 'btn-primary-submit'}`;
            overlay.classList.add('show');
        });
    }

    function closeConfirm() {
        const overlay = document.getElementById('confirmOverlay');
        if (overlay) {
            overlay.classList.remove('show');
        }
        if (_confirmResolve) {
            _confirmResolve(false);
            _confirmResolve = null;
        }
    }

    function doConfirm() {
        const overlay = document.getElementById('confirmOverlay');
        if (overlay) {
            overlay.classList.remove('show');
        }
        if (_confirmResolve) {
            _confirmResolve(true);
            _confirmResolve = null;
        }
    }

    // ===== 日期格式化 =====
    function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
        if (!date) return '-';
        const d = new Date(date);
        if (isNaN(d.getTime())) return '-';

        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');

        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes)
            .replace('ss', seconds);
    }

    // ===== JSON 格式化 =====
    function formatJSON(obj, spaces = 2) {
        try {
            return JSON.stringify(obj, null, spaces);
        } catch (e) {
            return String(obj);
        }
    }

    function parseJSON(str) {
        if (!str) return null;
        try {
            return JSON.parse(str);
        } catch (e) {
            return null;
        }
    }

    // ===== HTML 转义 =====
    function escapeHtml(str) {
        if (!str) return '-';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // ===== URL 参数解析 =====
    function getUrlParam(name) {
        const params = new URLSearchParams(window.location.search);
        return params.get(name);
    }

    // 暴露公共 API
    return {
        toast,
        showConfirm,
        closeConfirm,
        doConfirm,
        formatDate,
        formatJSON,
        parseJSON,
        escapeHtml,
        getUrlParam
    };
})();

// 全局导出，供父窗口调用
if (typeof window !== 'undefined') {
    window.utils = Utils;
}
