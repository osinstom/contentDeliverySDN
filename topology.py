from mininet.topo import Topo

class IcnTopo( Topo ):
    "Simple topology example."

    def __init__( self ):
        "Create custom topo."

        # Initialize topology
        Topo.__init__( self )

        # Add hosts and switches
        h1 =  [ self.addHost( 'h1')]
        h2 =  [ self.addHost( 'h2')]
        h3 =  [ self.addHost( 'h3')]

        s1 = [ self.addSwitch( 's1', dpid="0000000000000001")]
        s2 = [ self.addSwitch( 's2', dpid="0000000000000002")]
        s3 = [ self.addSwitch( 's3', dpid="0000000000000003")]
        s4 = [ self.addSwitch( 's4', dpid="0000000000000004")]
        s5 = [ self.addSwitch( 's5', dpid="0000000000000005")]


        #host to switch links
        self.addLink('s1','h1')
        self.addLink('s5','h2')
	self.addLink('s5','h3')

        self.addLink('s1','s2')
        self.addLink('s1','s3')
        self.addLink('s3','s4')
        self.addLink('s2','s3')
        self.addLink('s1','s4')
        self.addLink('s2','s5')
        self.addLink('s4','s5')


topos = { 'icntopology': ( lambda: IcnTopo() ) }
