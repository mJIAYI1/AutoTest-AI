import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElLoading,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElAlert)
app.use(ElButton)
app.use(ElDialog)
app.use(ElDrawer)
app.use(ElDropdown)
app.use(ElDropdownItem)
app.use(ElDropdownMenu)
app.use(ElEmpty)
app.use(ElForm)
app.use(ElFormItem)
app.use(ElInput)
app.use(ElLoading)
app.use(ElMenu)
app.use(ElMenuItem)
app.use(ElOption)
app.use(ElSelect)
app.use(ElSwitch)
app.use(ElTabPane)
app.use(ElTable)
app.use(ElTableColumn)
app.use(ElTabs)
app.use(ElTag)
app.mount('#app')
