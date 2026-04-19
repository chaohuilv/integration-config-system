/**
 * Pagination 分页组件
 * 
 * 统一的分页 UI 组件，支持多种后端分页格式（Spring Data Page / MyBatis-Plus Page）
 * 
 * 使用方式：
 *   1. 引入：<script src="../js/components/pagination.js"></script>
 *   2. HTML容器：<div class="pagination" id="pagination"></div>
 *   3. 初始化：
 *      const pager = new Pagination('#pagination', {
 *          pageSize: 10,                          // 每页条数
 *          onPageChange: function(page) { ... }    // 翻页回调
 *      });
 *   4. 渲染：
 *      pager.render({ total: 100, currentPage: 1 });
 *      // 或直接传后端分页数据，组件自动解析
 *      pager.render(pageData);  // Spring Data / MyBatis-Plus Page 对象
 * 
 * API：
 *   - render(data, currentPage?)  渲染分页
 *   - getCurrentPage()            获取当前页码
 *   - destroy()                   销毁组件，解绑事件
 */
;(function(window, $) {
    'use strict';

    const Pagination = function(selector, options) {
        this.$el = $(selector);
        if (!this.$el.length) {
            console.warn('[Pagination] 容器不存在:', selector);
            return;
        }
        this.options = $.extend({
            pageSize: 10,           // 每页条数
            maxVisible: 5,          // 最多显示的页码数（奇数，对称显示）
            showTotal: true,        // 是否显示总条数
            showPageSize: false,    // 是否显示每页条数选择
            pageSizes: [10, 20, 50, 100],
            prevText: '上一页',
            nextText: '下一页',
            onPageChange: null,     // function(page) 翻页回调
            onPageSizeChange: null  // function(pageSize) 每页条数变更回调
        }, options);

        this.currentPage = 1;
        this.totalElements = 0;
        this.totalPages = 0;
        this._namespace = '.pagination_' + Date.now();

        this._bindEvents();
    };

    /**
     * 从后端分页数据中提取统一的分页信息
     * 兼容 Spring Data Page 和 MyBatis-Plus Page 格式
     */
    Pagination.parsePageData = function(data) {
        if (!data) return { total: 0, totalPages: 0, currentPage: 1, pageSize: 10 };

        const list = data.content || data.records || data.list || [];
        const total = data.totalElements || data.total || 0;
        const totalPages = data.totalPages || (total > 0 ? Math.ceil(total / (data.size || data.pageSize || 10)) : 0);

        // Spring Data: number 从0开始; MyBatis-Plus: current 从1开始
        let currentPage;
        if (typeof data.number === 'number') {
            currentPage = data.number + 1; // Spring Data: 0-based → 1-based
        } else if (typeof data.current === 'number') {
            currentPage = data.current;
        } else {
            currentPage = 1;
        }

        const pageSize = data.size || data.pageSize || 10;

        return { total, totalPages, currentPage, pageSize, list };
    };

    Pagination.prototype = {

        /**
         * 渲染分页
         * @param {Object} data - 分页数据（支持多种格式）
         *   格式1（精简）: { total: 100, currentPage: 1 }
         *   格式2（Spring Data Page）: { content: [...], totalElements: 100, totalPages: 10, number: 0, size: 10 }
         *   格式3（MyBatis-Plus Page）: { records: [...], total: 100, pages: 10, current: 1, size: 10 }
         * @param {number} [currentPageOverride] - 可选，覆盖 data 中的当前页
         */
        render: function(data, currentPageOverride) {
            if (!this.$el.length) return this;

            // 如果只传了 total 数字
            if (typeof data === 'number') {
                data = { total: data };
            }

            const parsed = Pagination.parsePageData(data);
            this.totalElements = parsed.total;
            this.totalPages = parsed.totalPages || Math.ceil(parsed.total / this.options.pageSize);
            this.currentPage = currentPageOverride || parsed.currentPage || 1;

            // 确保 currentPage 在有效范围内
            if (this.currentPage < 1) this.currentPage = 1;
            if (this.currentPage > this.totalPages && this.totalPages > 0) this.currentPage = this.totalPages;

            this._renderHTML();
            return this;
        },

        /**
         * 获取当前页码
         */
        getCurrentPage: function() {
            return this.currentPage;
        },

        /**
         * 获取总页数
         */
        getTotalPages: function() {
            return this.totalPages;
        },

        /**
         * 销毁组件
         */
        destroy: function() {
            this.$el.off(this._namespace);
            this.$el.html('');
        },

        // ===== 内部方法 =====

        _renderHTML: function() {
            const opts = this.options;
            const total = this.totalPages;
            const cur = this.currentPage;

            // 0页或1页时不显示分页
            /*if (total <= 1) {
                this.$el.html('');
                return;
            }*/

            let html = '';

            // 上一页按钮
            html += `<button class="pg-btn pg-prev" data-action="prev" ${cur <= 1 ? 'disabled' : ''}>${opts.prevText}</button>`;

            // 页码
            const pages = this._getPageRange(cur, total);
            pages.forEach(p => {
                if (p === '...') {
                    html += '<span class="pg-ellipsis">...</span>';
                } else {
                    html += `<button class="pg-btn pg-num ${p === cur ? 'pg-active' : ''}" data-page="${p}">${p}</button>`;
                }
            });

            // 下一页按钮
            html += `<button class="pg-btn pg-next" data-action="next" ${cur >= total ? 'disabled' : ''}>${opts.nextText}</button>`;

            // 总条数
            if (opts.showTotal) {
                html += `<span class="pg-info">共 ${this.totalElements} 条</span>`;
            }

            // 每页条数选择
            if (opts.showPageSize) {
                html += '<select class="pg-size-select">';
                opts.pageSizes.forEach(s => {
                    html += `<option value="${s}" ${s === opts.pageSize ? 'selected' : ''}>${s}条/页</option>`;
                });
                html += '</select>';
            }

            this.$el.html(html);
        },

        /**
         * 计算需要显示的页码范围
         * 总是显示首尾页，中间显示当前页附近 maxVisible 个
         */
        _getPageRange: function(current, total) {
            const pages = [];
            const maxVisible = this.options.maxVisible;

            if (total <= maxVisible + 2) {
                // 总页数不多，全部显示
                for (let i = 1; i <= total; i++) pages.push(i);
                return pages;
            }

            // 始终显示第1页
            pages.push(1);

            // 计算中间范围
            const half = Math.floor(maxVisible / 2);
            let start = Math.max(2, current - half);
            let end = Math.min(total - 1, current + half);

            // 确保中间区域至少显示 maxVisible 个页码
            if (end - start + 1 < maxVisible) {
                if (start === 2) {
                    end = Math.min(total - 1, start + maxVisible - 1);
                } else {
                    start = Math.max(2, end - maxVisible + 1);
                }
            }

            // 省略号
            if (start > 2) pages.push('...');
            for (let i = start; i <= end; i++) pages.push(i);
            if (end < total - 1) pages.push('...');

            // 始终显示最后一页
            pages.push(total);

            return pages;
        },

        _bindEvents: function() {
            const self = this;
            const ns = this._namespace;

            // 使用事件委托，避免重复绑定
            this.$el.on('click' + ns, '.pg-btn:not(:disabled)', function() {
                const $btn = $(this);
                const action = $btn.data('action');
                const page = $btn.data('page');

                if (action === 'prev') {
                    self._goToPage(self.currentPage - 1);
                } else if (action === 'next') {
                    self._goToPage(self.currentPage + 1);
                } else if (page) {
                    self._goToPage(parseInt(page));
                }
            });

            // 每页条数选择
            this.$el.on('change' + ns, '.pg-size-select', function() {
                const newSize = parseInt($(this).val());
                self.options.pageSize = newSize;
                if (typeof self.options.onPageSizeChange === 'function') {
                    self.options.onPageSizeChange(newSize);
                }
                // 改变每页条数后回到第1页
                self._goToPage(1);
            });
        },

        _goToPage: function(page) {
            if (page < 1 || page > this.totalPages || page === this.currentPage) return;
            this.currentPage = page;
            if (typeof this.options.onPageChange === 'function') {
                this.options.onPageChange(page);
            }
        }
    };

    // 暴露到全局
    window.Pagination = Pagination;

})(window, jQuery);
