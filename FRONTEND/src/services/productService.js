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

  async searchProducts(keyword) {
    const res = await api.get(`/products/search`, { params: { keyword } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async createProduct(productData) {
    const res = await api.post('/products', productData);
    return res?.data !== undefined ? res.data : res;
  },

  async updateProduct(id, productData) {
    const res = await api.put(`/products/${id}`, productData);
    return res?.data !== undefined ? res.data : res;
  },
};
