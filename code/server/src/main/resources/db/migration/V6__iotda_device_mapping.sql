-- V6: 为 IoTDA 设备 ID 映射更新车载终端序列号
-- IoTDA device_id = 6a44f1047f2e6c302f80df85_vehicle_safety
-- node_id = vehicle_safety
-- 将 v001 的 terminal_sn 更新为 node_id，建立设备→车辆→行程映射链
UPDATE t_vehicle SET terminal_sn = 'vehicle_safety' WHERE vehicle_id = 'v001-c7e5-4ghi-a011-678901234hij';
