import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_TOKEN_KEY } from '../api/http'

const AppLayout = () => import('../layouts/AppLayout.vue')
const LoginView = () => import('../views/LoginView.vue')
const RegisterView = () => import('../views/RegisterView.vue')
const DashboardView = () => import('../views/DashboardView.vue')
const ProjectsView = () => import('../views/ProjectsView.vue')
const ProjectWorkspaceView = () => import('../views/ProjectWorkspaceView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true },
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: DashboardView },
        { path: 'projects', name: 'projects', component: ProjectsView },
        {
          path: 'projects/:projectId',
          name: 'project-workspace',
          component: ProjectWorkspaceView,
          props: (route) => ({ projectId: Number(route.params.projectId) }),
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem(AUTH_TOKEN_KEY))
  if (to.meta.requiresAuth && !hasToken) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && hasToken) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
