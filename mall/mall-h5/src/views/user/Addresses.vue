<template>
  <!-- 收货地址管理页：列表 + 新增/编辑/删除 -->
  <div class="page">
    <van-nav-bar title="收货地址" left-arrow @click-left="router.back()" />
    <!-- 地址列表 -->
    <van-cell-group inset title="我的地址">
      <van-empty v-if="addresses.length === 0" description="暂无地址，请添加" />
      <van-cell v-for="a in addresses" :key="a.id" :title="a.receiverName + ' ' + a.receiverPhone">
        <template #label>
          {{ a.detail }}
        </template>
        <template #value>
          <div class="addr-ops">
            <van-tag v-if="a.isDefault === 1" type="primary" class="addr-tag">默认</van-tag>
            <van-button size="mini" @click.stop="openEdit(a)">编辑</van-button>
            <van-button size="mini" type="danger" @click.stop="onDelete(a)">删除</van-button>
          </div>
        </template>
      </van-cell>
    </van-cell-group>
    <!-- 新增地址按钮 -->
    <div class="submit">
      <van-button v-if="!showForm" round block type="primary" @click="openAdd">新增地址</van-button>
    </div>
    <!-- 新增/编辑表单 -->
    <template v-if="showForm">
      <van-cell-group inset :title="editingId ? '编辑地址' : '新增地址'">
        <van-form @submit="onSave">
          <van-field
            v-model="form.receiverName"
            name="receiverName"
            label="收货人"
            placeholder="请输入收货人姓名"
            :rules="[{ required: true, message: '请填写收货人' }]"
          />
          <van-field
            v-model="form.receiverPhone"
            name="receiverPhone"
            label="联系电话"
            placeholder="请输入联系电话"
            :rules="[
              { required: true, message: '请填写联系电话' },
              { pattern: /^1\d{10}$/, message: '手机号格式不正确' }
            ]"
          />
          <van-field
            v-model="form.lng"
            type="number"
            name="lng"
            label="经度"
            placeholder="如 113.66"
            :rules="[{ required: true, message: '请填写经度' }]"
          />
          <van-field
            v-model="form.lat"
            type="number"
            name="lat"
            label="纬度"
            placeholder="如 34.76"
            :rules="[{ required: true, message: '请填写纬度' }]"
          />
          <van-field
            v-model="form.detail"
            name="detail"
            label="详细地址"
            placeholder="请输入详细地址"
            :rules="[{ required: true, message: '请填写详细地址' }]"
          />
          <van-field name="isDefault" label="设为默认地址">
            <template #input>
              <van-switch v-model="form.isDefault" size="20" />
            </template>
          </van-field>
          <div class="form-ops">
            <van-button round block type="primary" native-type="submit" :loading="saving">保 存</van-button>
            <van-button round block plain class="form-cancel" @click="closeForm">取 消</van-button>
          </div>
        </van-form>
      </van-cell-group>
    </template>
  </div>
</template>

<script setup>
// 地址管理页：新增/编辑/删除收货地址
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import api from '../../api'

const router = useRouter()

const addresses = ref([])
const showForm = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  lng: '',
  lat: '',
  detail: '',
  isDefault: false
})

// 加载地址列表
const load = async () => {
  try {
    addresses.value = (await api.addressList()) || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 打开新增表单
const openAdd = () => {
  editingId.value = null
  Object.assign(form, {
    receiverName: '',
    receiverPhone: '',
    lng: '113.66',
    lat: '34.76',
    detail: '',
    isDefault: false
  })
  showForm.value = true
}

// 打开编辑表单（回填数据）
const openEdit = (a) => {
  editingId.value = a.id
  Object.assign(form, {
    receiverName: a.receiverName,
    receiverPhone: a.receiverPhone,
    lng: a.lng,
    lat: a.lat,
    detail: a.detail,
    isDefault: a.isDefault === 1
  })
  showForm.value = true
}

// 关闭表单
const closeForm = () => {
  showForm.value = false
  editingId.value = null
}

// 保存（新增或编辑）
const onSave = async () => {
  saving.value = true
  try {
    const payload = {
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      lng: Number(form.lng),
      lat: Number(form.lat),
      detail: form.detail,
      isDefault: form.isDefault ? 1 : 0
    }
    if (editingId.value) {
      await api.addressUpdate(editingId.value, payload)
      showToast('修改成功')
    } else {
      await api.addressAdd(payload)
      showToast('添加成功')
    }
    closeForm()
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    saving.value = false
  }
}

// 删除地址（确认弹窗）
const onDelete = async (a) => {
  try {
    await showConfirmDialog({ title: '删除地址', message: '确认删除该地址吗？' })
  } catch (e) {
    return // 用户取消
  }
  try {
    await api.addressDelete(a.id)
    showToast('删除成功')
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}
.addr-ops {
  display: flex;
  align-items: center;
  gap: 6px;
}
.addr-tag {
  margin-right: 4px;
}
.submit {
  margin: 16px;
}
.form-ops {
  margin: 16px;
}
.form-cancel {
  margin-top: 12px;
}
</style>
