const mysql = require('mysql2/promise');

async function testDatabase() {
  try {
    const connection = await mysql.createConnection({
      host: 'localhost',
      user: 'root',
      password: '123456',
      database: 'pvcleaning'
    });
    
    // 查询用户表
    const [users] = await connection.execute('SELECT * FROM user LIMIT 5');
    console.log('用户表数据:', users);
    
    await connection.end();
  } catch (error) {
    console.error('数据库连接失败:', error.message);
  }
}

testDatabase();
