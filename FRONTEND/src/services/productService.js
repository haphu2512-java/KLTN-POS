import api from './api';

export const productService = {
  async getProducts() {
    const res = await api.get('/products');
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getProductById(id) {
    const res = await api.get(`/products/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async getProductByBarcode(barcode) {
    const res = await api.get(`/products/barcode/${encodeURIComponent(barcode)}`);
    return res?.data !== undefined ? res.data : res;
  },


  async createProduct(productData) {
    const res = await api.post('/products', productData);
    return res?.data !== undefined ? res.data : res;
  },

  async updateProduct(id, productData) {
    const res = await api.put(`/products/${id}`, productData);
    return res?.data !== undefined ? res.data : res;
  },

  /** Ngừng kinh doanh (xóa mềm): sản phẩm ẩn khỏi danh mục, lịch sử bán vẫn giữ */
  async deleteProduct(id) {
    return api.delete(`/products/${id}`);
  },

  /** ADMIN: gán mã nội bộ cho sản phẩm mang mã vạch cũ không quét được */
  async syncBarcodes() {
    const res = await api.post('/products/barcodes/sync');
    return res?.data !== undefined ? res.data : res;
  },
};
