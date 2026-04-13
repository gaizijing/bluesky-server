-- 为users表添加role字段
ALTER TABLE public.users ADD COLUMN role VARCHAR(20) DEFAULT 'user';

-- 更新现有用户的角色
UPDATE public.users SET role = 'admin' WHERE username = 'admin';
UPDATE public.users SET role = 'user' WHERE username IN ('fly_operator', 'monitor');

-- 为宁河区添加一个用户
INSERT INTO public.users (id, username, password, name, email, phone, status, role, last_login_time, login_count, created_at, updated_at, created_by, updated_by)
VALUES (
    'U004', 
    'ninghe_admin', 
    '$2a$10$2enCWfyf4FjHFpecHPgl1eLWoqko//fuR9IGRkWpmcwAsy5ng.RaK', 
    '宁河区管理员', 
    'ninghe_admin@example.com', 
    '13800138003', 
    'active', 
    'admin', 
    NULL, 
    0, 
    NOW(), 
    NOW(), 
    NULL, 
    NULL
) ON CONFLICT (id) DO NOTHING;
