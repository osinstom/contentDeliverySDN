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
        h4 =  [ self.addHost( 'h4')]
        h5 =  [ self.addHost( 'h5')]
        h6 =  [ self.addHost( 'h6')]

        s1 = [ self.addSwitch( 's1', dpid="0000000000000201")]
        s2 = [ self.addSwitch( 's2', dpid="0000000000000202")]
        s3 = [ self.addSwitch( 's3', dpid="0000000000000203")]
        s4 = [ self.addSwitch( 's4', dpid="0000000000000204")]
        s5 = [ self.addSwitch( 's5', dpid="0000000000000205")]
        s6 = [ self.addSwitch( 's6', dpid="0000000000000206")]


        #host to switch links
        self.addLink('s1','h1')
        self.addLink('s2','h2')
        self.addLink('s3','h3')
        self.addLink('s4','h4')
        self.addLink('s5','h5')
        self.addLink('s6','h6')

        self.addLink('s1','s4')
        self.addLink('s2','s5')
        self.addLink('s3','s6')


topos = { 'icntopology': ( lambda: IcnTopo() ) }
