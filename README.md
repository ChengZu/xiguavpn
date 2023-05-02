# 如果vps封udp, 用这个版本[https://github.com/ChengZu/easyvpn-tcp](https://github.com/ChengZu/easyvpn-tcp)

# 如何配置服务器（Ubuntu）

1.安装jdk
```  
apt install openjdk-11-jdk-headless
```  
2.运行程序
 
 将build/EasyVpn.jar 拷贝到服务器(用WinSCP), 执行下面命令运行
```  
 java -jar EasyVpn.jar
```  

# 如何安装客户端

1.将build/app-release.apk 拷贝到android手机上安装

2.ip选项填服务器IP地址,其他选项默认即可

3.点击启动
