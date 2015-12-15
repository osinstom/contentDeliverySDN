import mininet
from mininet.net import Mininet
from mininet.node import Controller, RemoteController, OVSController
from mininet.node import CPULimitedHost, Host, Node
from mininet.node import OVSKernelSwitch, UserSwitch
from mininet.node import IVSSwitch
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.link import TCLink, Intf
from subprocess import call

def IcnNetwork():

    net = Mininet( topo=None, build=False, ipBase='10.0.0.0/8')
    net.addController( 'c0', controller=RemoteController,
            ip='127.0.0.1',protocol='tcp', port=6653 )
    #Adding client's terminals
    h1 = net.addHost('h1', mac="00:00:00:00:00:01")
    h2 = net.addHost('h2', mac="00:00:00:00:00:02")
    h3 = net.addHost('h3', mac="00:00:00:00:00:03")
    hosts = [h1, h2, h3]
    #Adding content servers
    cs1 = net.addHost('cs1', ip="10.0.1.1", mac="00:00:00:00:00:11")
    cs2 = net.addHost('cs2', ip="10.0.1.2", mac="00:00:00:00:00:12")
    #cs3 = net.addHost('cs3')
    #cs4 = net.addHost('cs4')
    #cs5 = net.addHost('cs5')
    cses = [cs1, cs2]
    #Adding switches
    s1 = net.addSwitch('s1', dpid="0000000000000001")
    s2 = net.addSwitch('s2', dpid="0000000000000002")
    s3 = net.addSwitch('s3', dpid="0000000000000003")
    s4 = net.addSwitch('s4', dpid="0000000000000004")
    s5 = net.addSwitch('s5', dpid="0000000000000005")
    s6 = net.addSwitch('s6', dpid="0000000000000006")
    s7 = net.addSwitch('s7', dpid="0000000000000007")
    #s8 = net.addSwitch('s8', dpid="0000000000000008")

    #Adding switch-host links
    net.addLink(h1, s1)
    net.addLink(h2, s2)
    net.addLink(h3, s4)

    #Adding switch-server links
    net.addLink(s4, cs1)
    net.addLink(s7, cs2)
    
    #Adding switch-switch links
    net.addLink(s1, s2)
    net.addLink(s2, s4)
    net.addLink(s4, s5)
    net.addLink(s2, s3)
    net.addLink(s3, s4)
    net.addLink(s3, s1)
    net.addLink(s5, s6)
    net.addLink(s3, s7)
    net.addLink(s6, s7)
    net.addLink(s1, s7)

    net.start()
    for h in hosts:
        for i in range(0, len(cses)):
            h.cmd("arp -s 10.0.99.99 99:99:99:99:99:99")
            h.cmd("arp -s " + cses[i].IP() + " " + cses[i].MAC() )
    for cs in cses:
        for i in range(0, len(hosts)):
            cs.cmd("arp -s " + hosts[i].IP() + " " + hosts[i].MAC() )

    for cs in cses:
        cs.cmd("python -m SimpleHTTPServer 80 &")
    CLI(net)
    net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' )
    IcnNetwork()
