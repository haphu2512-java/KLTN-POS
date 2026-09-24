import api from './api';

export const productService = {
  async getProducts() {
    const res = await api.get('/products');
    return res.data || [];
  },

  async getProductById(id) {
    const res = await api.get(`/products/${id}`);
    return res.data;
  },

  async getProductByBarcode(barcode) {
    const res = await api.get(`/products/barcode/${encodeURIComponent(barcode)}`);
    return res.data;
  },

  async searchProducts(keyword) {
    const res = await api.get(`/products/search`, { params: { keyword } });
    return res.data || [];
  },

  async createProduct(productData) {
    const res = await api.post('/products', productData);
    return res.data;
  },

  async updateProduct(id, productData) {
    const res = await api.put(`/products/${id}`, productData);
    return res.data;
  },
};
