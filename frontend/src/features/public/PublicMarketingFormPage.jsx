import { useEffect, useState } from 'react'
import { CheckCircle2, Send, Sparkles } from 'lucide-react'
import { api } from '../../api'
import { Button, Field } from '../../components'

function resolveFormCode(routeKey) {
  const value = String(routeKey || '')
  if (!value.startsWith('public/forms/')) return ''
  return decodeURIComponent(value.slice('public/forms/'.length).split('?')[0])
}

function splitOptions(value) {
  return String(value || '')
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function defaultValue(fields = []) {
  const values = {}
  fields.forEach((field) => {
    values[field.fieldKey] = ''
  })
  return values
}

function renderFieldInput(field, value, onChange) {
  const commonProps = {
    value: value || '',
    onChange: (event) => onChange(field.fieldKey, event.target.value),
    placeholder: field.placeholder || '',
  }
  if (field.fieldType === 'TEXTAREA') {
    return <textarea rows="4" {...commonProps} />
  }
  if (field.fieldType === 'SELECT') {
    return (
      <select value={value || ''} onChange={(event) => onChange(field.fieldKey, event.target.value)}>
        <option value="">请选择</option>
        {splitOptions(field.optionsText).map((item) => <option value={item} key={item}>{item}</option>)}
      </select>
    )
  }
  if (field.fieldType === 'PHONE') {
    return <input type="tel" {...commonProps} />
  }
  if (field.fieldType === 'EMAIL') {
    return <input type="email" {...commonProps} />
  }
  return <input {...commonProps} />
}

export function PublicMarketingFormPage({ routeKey, logo }) {
  const formCode = resolveFormCode(routeKey)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState(null)
  const [values, setValues] = useState({})
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const data = await api.channel.publicFormDetail(formCode)
        if (!mounted) return
        setForm(data)
        setValues(defaultValue(data.fields || []))
      } catch (err) {
        if (mounted) setError(err.message || '表单加载失败')
      } finally {
        if (mounted) setLoading(false)
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [formCode])

  const update = (key, value) => {
    setValues({ ...values, [key]: value })
  }

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    for (const field of form?.fields || []) {
      if (field.requiredField && !String(values[field.fieldKey] || '').trim()) {
        setError(`${field.label}不能为空`)
        return
      }
    }
    setSubmitting(true)
    try {
      const response = await api.channel.publicFormSubmit({ formCode, values })
      setSuccess(response)
    } catch (err) {
      setError(err.message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="public-form-page">
      <div className="public-form-card">
        <div className="public-form-brand">
          {logo ? <img src={logo} alt="logo" /> : <span><Sparkles size={22} /></span>}
        </div>

        {loading ? (
          <div className="public-form-state">正在加载表单…</div>
        ) : error && !form ? (
          <div className="public-form-state error">{error}</div>
        ) : success ? (
          <div className="public-form-success">
            <CheckCircle2 size={42} />
            <h1>提交成功</h1>
            <p>{success.message || form.submitMessage || '提交成功，我们会尽快联系您。'}</p>
          </div>
        ) : (
          <>
            <div className="public-form-head">
              <h1>{form.title}</h1>
              {form.description && <p>{form.description}</p>}
            </div>
            <form className="public-form-fields" onSubmit={submit}>
              {(form.fields || []).map((field) => (
                <Field label={field.label} required={field.requiredField} key={field.fieldKey}>
                  {renderFieldInput(field, values[field.fieldKey], update)}
                </Field>
              ))}
              {error && <div className="form-error">{error}</div>}
              <Button type="submit" icon={Send} disabled={submitting}>
                {submitting ? '提交中' : '提交'}
              </Button>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
