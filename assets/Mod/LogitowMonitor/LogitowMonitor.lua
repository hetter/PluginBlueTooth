--[[
Title: bluetooth monitor
Author(s): dummy
Date: 
Desc: 
use the lib:
------------------------------------------------------------
NPL.load("(gl)Mod/LogitowMonitor/LogitowMonitor.lua");
local LogitowMonitor = commonlib.gettable("Mod.LogitowMonitor.LogitowMonitor");
------------------------------------------------------------
]]

local GameLogic = commonlib.gettable("MyCompany.Aries.Game.GameLogic")
local EntityManager = commonlib.gettable("MyCompany.Aries.Game.EntityManager");
local PluginBlueTooth = commonlib.gettable("Mod.PluginBlueTooth");

local LogitowMonitor = commonlib.gettable("Mod.LogitowMonitor.LogitowMonitor");

local BlueConstants = {
	READ_BLOCK_SERVER = "";--数据通讯的服务UUID  HEART_RATE_MEASUREMENT
	READ_BLOCK_CHARACTERISTIC = "";--可读对象UUID CLIENT_CHARACTERISTIC_CONFIG
	WRITE_BLOCK_CONFIG = "";--模块驱动的服务UUID HEART_RATE_MODEL
	WRITE_CHARACTERISTIC_CONFIG = "";--可写对象UUID
	MODEL_CLIENT_CHARACTERISTIC = "";--可读对象UUID
	BATTERY_DESC = "";
	WRITE_GET_BATTERY_HEX = "";
	LOGITOW_DEVICE = "LOGITOW";
}

function LogitowMonitor.setup(pluginBlueTooth)	
	LogitowMonitor.pluginBlueTooth = pluginBlueTooth;
	GameLogic:GetFilters():add_filter("blueTooth_set_blueStatus", LogitowMonitor.onBlueStatus);
	GameLogic:GetFilters():add_filter("blueTooth_check_device", LogitowMonitor.OnCheckDevice);
	GameLogic:GetFilters():add_filter("blueTooth_read_blueGattUuid", LogitowMonitor.OnReadGattUUID);
	GameLogic:GetFilters():add_filter("blueTooth_on_characteristic", LogitowMonitor.OnCharacteristic);
	GameLogic:GetFilters():add_filter("blueTooth_on_descriptor", LogitowMonitor.OnDescriptor);
end

function LogitowMonitor.onBlueStatus(isConnect)
	
end	

function LogitowMonitor.OnCheckDevice(_, device_params)
	if device_params.name == BlueConstants.LOGITOW_DEVICE and device_params.rssi > -70 then
		return true;
	end
	return _;
end	

function LogitowMonitor.OnReadGattUUID(uuidMap)
	LogitowMonitor.fatherMap = {};
	LogitowMonitor.uuidMap = uuidMap;
	
	
	for serId, chas in pairs(uuidMap) do
		for chaId, decss in pairs(chas) do
			LogitowMonitor.fatherMap[chaId] = serId;
			for decId, _ in pairs(decss) do
				LogitowMonitor.fatherMap[decId] = chaId;
			end
		end
	end

	
	for serId, chas in pairs(uuidMap) do
		commonlib.echo("--------------------------OnReadGattUUID serId:" .. serId);
		
		if(serId == BlueConstants.READ_BLOCK_SERVER) then
			for chaId, decss in pairs(chas) do
				commonlib.echo("--------------------------OnReadGattUUID chaId:" .. chaId);
				--LogitowMonitor.fatherMap[chaId] = serId;
				if BlueConstants.READ_BLOCK_CHARACTERISTIC == chaId  then
					commonlib.echo("--------------------------OnReadGattUUID READ_BLOCK_CHARACTERISTIC:")
					--LogitowMonitor.pluginBlueTooth:setCharacteristicNotification(serId, chaId, false);
					--LogitowMonitor.pluginBlueTooth:readCharacteristic(serId, chaId);
					LogitowMonitor.pluginBlueTooth:setCharacteristicNotification(serId, chaId, true);
					--LogitowMonitor.fatherMap[decId] = chaId;
					for decId, _ in pairs(decss) do
						commonlib.echo("--------------------------OnReadGattUUID decId:" .. decId);
						if (BlueConstants.BATTERY_DESC == decId) then
							LogitowMonitor.pluginBlueTooth:setDescriptorNotification(serId, chaId, decId);
						end
					end				
				end
			end
		end
	end
end

function LogitowMonitor.OnCharacteristic(params)
	commonlib.echo(string.format("-------------------------- OnCharacteristic uuid:%s status:%s io:%s", params.uuid, params.status, params.io));
	local str = LogitowMonitor.pluginBlueTooth:characteristicGetStrValue(LogitowMonitor.fatherMap[params.uuid], params.uuid);
	commonlib.echo(string.format("-------------------------- characteristicGetStrValue len:%s data:%s", str.len, str.data));
end	

function LogitowMonitor.OnDescriptor(params)
	if BlueConstants.READ_BLOCK_CHARACTERISTIC == LogitowMonitor.fatherMap[params.uuid] then
		commonlib.echo("--------------------------OnDescriptor return return -end2:" .. params.uuid);
		LogitowMonitor.getBleLevel();
		return;
	end	
	
	for serId, chas in pairs(LogitowMonitor.uuidMap) do
		commonlib.echo("--------------------------OnDescriptor serId:" .. serId);
		if serId == BlueConstants.WRITE_BLOCK_CONFIG then
			for chaId, decss in pairs(chas) do
				commonlib.echo("--------------------------OnDescriptor chaId:" .. chaId);
				if BlueConstants.WRITE_CHARACTERISTIC_CONFIG == chaId  then
					commonlib.echo("--------------------------OnDescriptor READ_BLOCK_CHARACTERISTIC:")
					--LogitowMonitor.pluginBlueTooth:setCharacteristicNotification(serId, chaId, false);
					--LogitowMonitor.pluginBlueTooth:readCharacteristic(serId, chaId);
					LogitowMonitor.pluginBlueTooth:setCharacteristicNotification(serId, chaId, true);
					
					for decId, _ in pairs(decss) do
						commonlib.echo("--------------------------OnDescriptor decId:" .. decId);
						if (BlueConstants.BATTERY_DESC == decId) then
							LogitowMonitor.pluginBlueTooth:setDescriptorNotification(serId, chaId, decId);
						end
					end				
				end
			end
		end
	end
end	

function LogitowMonitor.getBleLevel()

	if not LogitowMonitor.mytimer then
		LogitowMonitor.pluginBlueTooth:setCharacteristicNotification(BlueConstants.WRITE_BLOCK_CONFIG, BlueConstants.WRITE_CHARACTERISTIC_CONFIG, true);
		LogitowMonitor.pluginBlueTooth:setDescriptorNotification(BlueConstants.WRITE_BLOCK_CONFIG, BlueConstants.WRITE_CHARACTERISTIC_CONFIG, BlueConstants.BATTERY_DESC);
		
		LogitowMonitor.mytimer = commonlib.Timer:new({callbackFunc = function(timer)
			LogitowMonitor.getBleLevel();
		end})
		LogitowMonitor.mytimer:Change(100, 100);
	end
	
	local writeData = BlueConstants.WRITE_GET_BATTERY_HEX;
	LogitowMonitor.pluginBlueTooth:writeToCharacteristic(BlueConstants.WRITE_BLOCK_CONFIG, BlueConstants.WRITE_CHARACTERISTIC_CONFIG, writeData);
end