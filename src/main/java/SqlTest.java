import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.oracle.parser.OracleStatementParser;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleSchemaStatVisitor;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.google.common.base.CaseFormat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SqlTest {

    public static void main(String[] args) throws Exception {
//        需处理的sql
        String sql = "SELECT DESCR FROM CI_SP_TYPE_L WHERE SP_TYPE_CD = ? AND LANGUAGE_CD = ?";
//        表对应关系Excel
        String tablePath = "C:\\Users\\user\\Desktop\\Database_Script_Record.xlsx";
//        字段对应关系Excel
        String colunmnPath = "C:\\Users\\user\\Desktop\\Data Conversion Specification(SAM 20181228).xlsm";
//        获取对应关系
        List list = getMapping(tablePath,colunmnPath);
//        表对应关系
        Map<String,String> tableMapping = (Map<String,String>)list.get(0);
//      字段对应关系
        Map<String,Map<String,Map<String,String>>> mapping = (Map<String,Map<String,Map<String,String>>>)list.get(1);
//        处理SQL
        sqlParseChange(sql,tableMapping,mapping);

    }

    /**
     * 替换SQL
     * @param sql
     * @param tableMapping
     * @param mapping
     */
    public static void sqlParseChange(String sql,Map<String,String> tableMapping ,Map<String,Map<String,Map<String,String>>> mapping){
        // 新建 Oracle Parser
        SQLStatementParser parser = new OracleStatementParser(sql);

        // 使用Parser解析生成AST，这里SQLStatement就是AST
        SQLStatement statement = parser.parseStatement();

        // 使用visitor来访问AST,根据需要选择数据库
        OracleSchemaStatVisitor visitor = new OracleSchemaStatVisitor();
        statement.accept(visitor);

        Map<String,String> replaceMap = new HashMap<String,String>();
        Map<String,String> dealReplaceMap = new HashMap<String,String>();
//        表
        for (TableStat.Name name : visitor.getTables().keySet()) {
            String newTableName = tableMapping.get(name.getName());
            try{
                String dealnewTableName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, newTableName);
//            表名前后有空白
                replaceMap.put(" "+name.getName()+" "," "+newTableName+" ");
                dealReplaceMap.put(" "+name.getName()+" "," "+dealnewTableName+" ");
            }catch (Exception e){
//                e.printStackTrace();
                print("新表找不到："+name.getName()+"--"+newTableName);
            }

        }
//       字段
        Map<String,String> newreplaceTable = new HashMap<String, String>();

        Map<String,String> replaceColumnMap = new HashMap<String,String>();
        for (TableStat.Column column : visitor.getColumns()) {
            String newTableName = tableMapping.get(column.getTable());
            try{
                String newColumn = mapping.get(column.getTable()).get(newTableName).get(column.getName());
                String dealNewColumn = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, newColumn);   //驼峰命名
                replaceColumnMap.put(column.getName(),newColumn);
                newreplaceTable.put(column.getName(),dealNewColumn);
            }catch (Exception e){
//                e.printStackTrace();
                print("新表找不到："+column.getTable()+"--"+newTableName);
            }

        }
        print("-------------替换表--------------------");
        print(replaceMap);
        print(dealReplaceMap);
        print("-------------替换字段--------------------");
        print(replaceColumnMap);
        print(newreplaceTable);
        replaceMap.putAll(replaceColumnMap);
        dealReplaceMap.putAll(newreplaceTable);
        print("-------------原始sql--------------------");
        print(sql);
        String replaceSql = sql+"";
//       替换旧表、旧字段，使用新表、新字段
        for (String key : replaceMap.keySet()) {
            replaceSql = replaceSql.replace(key,replaceMap.get(key));
        }
        print("---------------新表sql------------------");
        print(replaceSql);
        String replaceNewSql = sql+"";
//       替换旧表、旧字段，使用实际代码中使用的新表、新字段 （驼峰命名）
//        此处可能替换错误的内容
        for (String key : dealReplaceMap.keySet()) {
            replaceNewSql = replaceNewSql.replace(key,dealReplaceMap.get(key));
        }
        print("---------------代码使用sql------------------");
        print(replaceNewSql);


    }

    /**
     * 获取旧表和新表，旧字段和新字段的对应关系
     * @return
     * @throws Exception
     */
    public static List getMapping(String tablePath,String colunmnPath) throws Exception{
        List<List<String>> tableData = readExcel(tablePath);
        List<List<String>> columnData = readExcel(colunmnPath);
//      旧表名->新表名->旧字段名->新字段名
        Map<String,Map<String,Map<String,String>>> mapping = new HashMap<String,Map<String,Map<String,String>>>();
//       旧表名->新表名
        Map<String,String> tableMapping = new HashMap<String,String>();
//       旧表->新表
        for (int i = 4; i<tableData.size();i++) {
            if(!(checkIsNull(tableData.get(i).get(0))||checkIsNull(tableData.get(i).get(2)))){
                tableMapping.put(tableData.get(i).get(0),tableData.get(i).get(2));

                Map<String,Map<String,String>> map = new HashMap<String,Map<String,String>>();
                map.put(tableData.get(i).get(2),new HashMap<String,String>());
                mapping.put(tableData.get(i).get(0),map);
            }
        }
//        旧字段->新字段
        for (List<String> list : columnData) {
            if(!(checkIsNull(list.get(1))||checkIsNull(list.get(2))||checkIsNull(list.get(8))||checkIsNull(list.get(9)))){
                if (mapping.containsKey(list.get(1))&&mapping.get(list.get(1)).containsKey(list.get(8))){
                    mapping.get(list.get(1)).get(list.get(8)).put(list.get(2),list.get(9));
                }
            }
        }
        List list = new ArrayList();
        list.add(tableMapping);
        list.add(mapping);
        return list;
    }

    /**
     * 打印到控制台
     * @param object
     */
    public static void print(Object object){
        System.out.println(object.toString());
    }

    /**
     * 读取Excel数据
     * @param path
     * @return
     * @throws Exception
     */
    public static List<List<String>> readExcel(String path) throws Exception{
        InputStream inputStrem  = new FileInputStream(path);
        return EasyExcelUtil.readExcelWithStringList(inputStrem, ExcelTypeEnum.XLSX);
    }

    /**
     * 检测空值
     * @param str
     * @return
     */
    public static Boolean checkIsNull(String str){
        if(str==null) return true;
        if("null".equals(str)||"".equals(str)) return true;
        return false;
    }

}
