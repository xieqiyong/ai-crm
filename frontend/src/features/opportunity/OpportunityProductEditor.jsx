import { Plus, Trash2 } from 'lucide-react'
import { Button } from '../../components'
import { productOptionLabel } from '../../hooks/useProductOptions'

export const emptyOpportunityProductLine = {
  productId: '',
  productName: '',
  quantity: '1',
  unitPrice: '',
  discountRate: '100',
  unit: '',
  remark: '',
}

export function normalizeOpportunityProducts(products = []) {
  return (products || []).map((item) => ({
    id: item.id,
    productId: item.productId || '',
    productCode: item.productCode || '',
    productName: item.productName || '',
    category: item.category || '',
    productType: item.productType || '',
    quantity: item.quantity == null ? '1' : String(item.quantity),
    unitPrice: item.unitPrice == null ? '' : String(item.unitPrice),
    discountRate: item.discountRate == null ? '100' : String(item.discountRate),
    subtotal: item.subtotal,
    unit: item.unit || '',
    remark: item.remark || '',
  }))
}

export function toOpportunityProductPayload(products = []) {
  return normalizeOpportunityProducts(products)
    .filter((item) => item.productId || item.productName)
    .map((item) => ({
      id: item.id || null,
      productId: item.productId || null,
      productName: item.productName || null,
      quantity: item.quantity === '' ? null : item.quantity,
      unitPrice: item.unitPrice === '' ? null : item.unitPrice,
      discountRate: item.discountRate === '' ? null : item.discountRate,
      unit: item.unit || null,
      remark: item.remark || null,
    }))
}

export function calculateProductSubtotal(item) {
  const quantity = Number(item.quantity || 0)
  const unitPrice = Number(item.unitPrice || 0)
  const discountRate = item.discountRate === '' ? 100 : Number(item.discountRate || 0)
  return quantity * unitPrice * discountRate / 100
}

export function calculateProductTotal(products = []) {
  return normalizeOpportunityProducts(products)
    .reduce((total, item) => total + calculateProductSubtotal(item), 0)
}

function formatAmount(value) {
  const amount = Number(value || 0)
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

export function OpportunityProductEditor({ products = [], productOptions = [], onChange }) {
  const rows = normalizeOpportunityProducts(products)
  const updateLine = (index, patch) => {
    onChange(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)))
  }
  const addLine = () => {
    onChange([...rows, { ...emptyOpportunityProductLine }])
  }
  const removeLine = (index) => {
    onChange(rows.filter((item, itemIndex) => itemIndex !== index))
  }
  const selectProduct = (index, value) => {
    const product = productOptions.find((item) => String(item.id) === String(value))
    if (!product) {
      updateLine(index, { productId: '', productName: '', unitPrice: '', unit: '' })
      return
    }
    updateLine(index, {
      productId: product.id,
      productName: product.name || '',
      unitPrice: product.price == null ? '' : String(product.price),
      unit: product.unit || '',
    })
  }

  return (
    <div className="opportunity-product-editor">
      <div className="opportunity-product-head">
        <div>
          <b>产品与报价</b>
          <small>有产品明细时，商机金额自动汇总。</small>
        </div>
        <Button type="button" variant="secondary" icon={Plus} onClick={addLine}>添加产品</Button>
      </div>
      {rows.length ? (
        <div className="opportunity-product-lines">
          {rows.map((item, index) => (
            <div className="opportunity-product-line" key={index}>
              <select value={item.productId || ''} onChange={(event) => selectProduct(index, event.target.value)}>
                <option value="">自定义产品</option>
                {productOptions.map((product) => (
                  <option value={product.id} key={product.id}>{productOptionLabel(product)}</option>
                ))}
              </select>
              <input
                value={item.productName || ''}
                disabled={Boolean(item.productId)}
                onChange={(event) => updateLine(index, { productName: event.target.value })}
                placeholder="产品名称"
              />
              <input
                type="number"
                min="0.0001"
                step="0.0001"
                value={item.quantity || ''}
                onChange={(event) => updateLine(index, { quantity: event.target.value })}
                placeholder="数量"
              />
              <input
                type="number"
                min="0"
                step="0.01"
                value={item.unitPrice || ''}
                onChange={(event) => updateLine(index, { unitPrice: event.target.value })}
                placeholder="单价"
              />
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={item.discountRate || ''}
                onChange={(event) => updateLine(index, { discountRate: event.target.value })}
                placeholder="折扣%"
              />
              <input
                value={item.unit || ''}
                onChange={(event) => updateLine(index, { unit: event.target.value })}
                placeholder="单位"
              />
              <span>{formatAmount(calculateProductSubtotal(item))}</span>
              <button type="button" className="icon-button" onClick={() => removeLine(index)}>
                <Trash2 size={16} />
              </button>
            </div>
          ))}
        </div>
      ) : (
        <div className="opportunity-product-empty">
          暂未选择产品，商机金额会使用手填预估金额。
        </div>
      )}
      <div className="opportunity-product-total">
        <span>产品合计</span>
        <b>{formatAmount(calculateProductTotal(rows))}</b>
      </div>
    </div>
  )
}

export function OpportunityProductList({ products = [] }) {
  const rows = normalizeOpportunityProducts(products)
  if (!rows.length) {
    return (
      <div className="opportunity-product-empty">
        暂无产品明细
      </div>
    )
  }
  return (
    <div className="opportunity-product-list">
      {rows.map((item) => (
        <div className="opportunity-product-view" key={item.id || `${item.productName}-${item.productId}`}>
          <div>
            <b>{item.productName || '-'}</b>
            <small>{item.productCode || item.category || '产品快照'}</small>
          </div>
          <span>{item.quantity || 0}{item.unit || ''}</span>
          <span>{formatAmount(item.unitPrice)}</span>
          <span>{item.discountRate || 100}%</span>
          <strong>{formatAmount(item.subtotal == null ? calculateProductSubtotal(item) : item.subtotal)}</strong>
        </div>
      ))}
    </div>
  )
}
