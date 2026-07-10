/**
 * 车载安全监测系统 — 全部单元测试入口
 *
 * 引入各模块测试套件并统一注册。
 * 运行方式：Deveco Studio → 运行 entry → ohosTest / ohpm run test
 */
import jsonParserTest from './JsonParser.test';
import modelAuthTest from './ModelAuth.test';
import modelDriverTest from './ModelDriver.test';
import modelGuardianshipTest from './ModelGuardianship.test';
import modelFleetTest from './ModelFleet.test';
import viewStateTest from './ViewState.test';
import statusMapTest from './StatusMapUtils.test';
import sessionStoreTest from './SessionStore.test';

export default function testsuite() {
  jsonParserTest();
  modelAuthTest();
  modelDriverTest();
  modelGuardianshipTest();
  modelFleetTest();
  viewStateTest();
  statusMapTest();
  sessionStoreTest();
}
