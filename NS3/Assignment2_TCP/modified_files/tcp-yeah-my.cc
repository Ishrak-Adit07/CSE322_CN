/*
 * Copyright (c) 2016 ResiliNets, ITTC, University of Kansas
 *
 * SPDX-License-Identifier: GPL-2.0-only
 *
 * Author: Truc Anh N. Nguyen <annguyen@ittc.ku.edu>
 *
 * James P.G. Sterbenz <jpgs@ittc.ku.edu>, director
 * ResiliNets Research Group  https://resilinets.org/
 * Information and Telecommunication Technology Center (ITTC)
 * and Department of Electrical Engineering and Computer Science
 * The University of Kansas Lawrence, KS USA.
 */

#include "tcp-yeah-my.h"

#include "ns3/log.h"

using namespace std;

namespace ns3
{

NS_LOG_COMPONENT_DEFINE("TcpYeahMy");
NS_OBJECT_ENSURE_REGISTERED(TcpYeahMy);

double decayFactor = 1;

TypeId
TcpYeahMy::GetTypeId()
{
    static TypeId tid =
        TypeId("ns3::TcpYeahMy")
            .SetParent<TcpNewReno>()
            .AddConstructor<TcpYeahMy>()
            .SetGroupName("Internet")
            .AddAttribute("Alpha",
                          "Maximum backlog allowed at the bottleneck queue",
                          UintegerValue(80),
                          MakeUintegerAccessor(&TcpYeahMy::m_alpha),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Gamma",
                          "Fraction of queue to be removed per RTT",
                          UintegerValue(1),
                          MakeUintegerAccessor(&TcpYeahMy::m_gamma),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Delta",
                          "Log minimum fraction of cwnd to be removed on loss",
                          UintegerValue(3),
                          MakeUintegerAccessor(&TcpYeahMy::m_delta),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Epsilon",
                          "Log maximum fraction to be removed on early decongestion",
                          UintegerValue(1),
                          MakeUintegerAccessor(&TcpYeahMy::m_epsilon),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Phy",
                          "Maximum delta from base",
                          UintegerValue(8),
                          MakeUintegerAccessor(&TcpYeahMy::m_phy),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Rho",
                          "Minimum # of consecutive RTT to consider competition on loss",
                          UintegerValue(16),
                          MakeUintegerAccessor(&TcpYeahMy::m_rho),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("Zeta",
                          "Minimum # of state switches to reset m_renoCount",
                          UintegerValue(50),
                          MakeUintegerAccessor(&TcpYeahMy::m_zeta),
                          MakeUintegerChecker<uint32_t>())
            .AddAttribute("StcpAiFactor",
                          "STCP additive increase factor",
                          UintegerValue(500),
                          MakeUintegerAccessor(&TcpYeahMy::m_stcpAiFactor),
                          MakeUintegerChecker<uint32_t>());
    return tid;
}

TcpYeahMy::TcpYeahMy()
    : TcpNewReno(),
      m_alpha(80), // 80
      m_gamma(1), // 1
      m_delta(3),
      m_epsilon(1),
      m_phy(8), // 8
      m_rho(16), // 16
      m_zeta(50),
      m_stcpAiFactor(500), // 100
      m_stcp(nullptr),
      m_baseRtt(Time::Max()),
      m_minRtt(Time::Max()),
      m_cntRtt(0),
      m_doingYeahNow(true),
      m_begSndNxt(0),
      m_lastQ(0),
      m_doingRenoNow(0),
      m_renoCount(2),
      m_fastCount(0)
{
    NS_LOG_FUNCTION(this);
    m_stcp = CreateObject<TcpScalable>();
    m_stcp->SetAttribute("AIFactor", static_cast<UintegerValue>(m_stcpAiFactor));
}

TcpYeahMy::TcpYeahMy(const TcpYeahMy& sock)
    : TcpNewReno(sock),
      m_alpha(sock.m_alpha),
      m_gamma(sock.m_gamma),
      m_delta(sock.m_delta),
      m_epsilon(sock.m_epsilon),
      m_phy(sock.m_phy),
      m_rho(sock.m_rho),
      m_zeta(sock.m_zeta),
      m_stcpAiFactor(sock.m_stcpAiFactor),
      m_baseRtt(sock.m_baseRtt),
      m_minRtt(sock.m_minRtt),
      m_cntRtt(sock.m_cntRtt),
      m_doingYeahNow(sock.m_doingYeahNow),
      m_begSndNxt(sock.m_begSndNxt),
      m_lastQ(sock.m_lastQ),
      m_doingRenoNow(sock.m_doingRenoNow),
      m_renoCount(sock.m_renoCount),
      m_fastCount(sock.m_fastCount)
{
    NS_LOG_FUNCTION(this);
    m_stcp = CopyObject(sock.m_stcp);
}

TcpYeahMy::~TcpYeahMy()
{
    NS_LOG_FUNCTION(this);
}

Ptr<TcpCongestionOps>
TcpYeahMy::Fork()
{
    return CopyObject<TcpYeahMy>(this);
}

void
TcpYeahMy::PktsAcked(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked, const Time& rtt)
{
    NS_LOG_FUNCTION(this << tcb << segmentsAcked << rtt);

    if (rtt.IsZero())
    {
        return;
    }

    // Modification
    // double minRttFactor = 0.6;
    // m_minRtt = Time::FromDouble(minRttFactor * m_minRtt.GetSeconds() + (1 - minRttFactor) * rtt.GetSeconds(), Time::S);
    // Modification

    m_minRtt = std::min(m_minRtt, rtt);
    NS_LOG_DEBUG("Updated m_minRtt = " << m_minRtt.GetMilliSeconds() << " ms");

    m_baseRtt = std::min(m_baseRtt, rtt);
    NS_LOG_DEBUG("Updated m_baseRtt = " << m_baseRtt.GetMilliSeconds() << " ms");

    // Update RTT counter
    m_cntRtt++;
    NS_LOG_DEBUG("Updated m_cntRtt = " << m_cntRtt);
}

void
TcpYeahMy::EnableYeah(const SequenceNumber32& nextTxSequence)
{
    NS_LOG_FUNCTION(this << nextTxSequence);

    m_doingYeahNow = true;
    m_begSndNxt = nextTxSequence;
    m_cntRtt = 0;
    m_minRtt = Time::Max();
}

void
TcpYeahMy::DisableYeah()
{
    NS_LOG_FUNCTION(this);

    m_doingYeahNow = false;
}

void
TcpYeahMy::CongestionStateSet(Ptr<TcpSocketState> tcb,
                              const TcpSocketState::TcpCongState_t newState)
{
    NS_LOG_FUNCTION(this << tcb << newState);

    if (newState == TcpSocketState::CA_OPEN)
    {
        EnableYeah(tcb->m_nextTxSequence);
    }
    else
    {
        DisableYeah();
    }
}

void
TcpYeahMy::IncreaseWindow(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked)
{
    NS_LOG_FUNCTION(this << tcb << segmentsAcked);

    if (tcb->m_cWnd < tcb->m_ssThresh)
    {
        NS_LOG_LOGIC("In slow start, invoke NewReno slow start.");

        // Modification
        // Good for > 0.5 and < 1.0
        // Bad for  < 0.5 [cwnd drops in congestion aviodance phase] and > 1.0 [Initially drops too much]
        decayFactor = 0.6;
        // Modification

        TcpNewReno::SlowStart(tcb, segmentsAcked);
    }
    else if (!m_doingRenoNow)
    { // Fast mode
        NS_LOG_LOGIC("In Fast mode, increment cwnd according to STCP rule.");

        // Modification
        decayFactor = 1.0;
        // Modification

        m_stcp->IncreaseWindow(tcb, segmentsAcked);
        NS_LOG_INFO("In Fast mode, updated to cwnd " << tcb->m_cWnd << " ssthresh "
                                                     << tcb->m_ssThresh);
    }
    else
    { // Behave like NewReno

        // Modification
        decayFactor = 1.0;
        // Modification

        TcpNewReno::CongestionAvoidance(tcb, segmentsAcked);
    }

    if (tcb->m_lastAckedSeq >= m_begSndNxt)
    { // A YeAH cycle has finished, we do YeAH cwnd adjustment every RTT.

        NS_LOG_LOGIC("A YeAH cycle has finished, check if enough RTT samples.");
        /*
         * We perform YeAH calculations only if we got enough RTT samples to
         * insure that at least 1 of those samples wasn't from a delayed ACK.
         */
        // Modification
        // if (m_cntRtt > 20)
        if (m_cntRtt > 2)
        // Modification
        {
            //cout << m_cntRtt << endl;
            NS_LOG_LOGIC("Enough RTT samples to perform YeAH calculations");
            /*
             * We have enough RTT samples to perform YeAH algorithm.
             * Now we need to determine if we should operate in Fast or Slow mode,
             * and if we should execute the precautionary decongestion algorithm.
             */

            uint32_t segCwnd = tcb->GetCwndInSegments();

            // Calculate the extra number of packets in queue
            // Naming convention: minRtt is the minimum RTT of this round,
            // baseRtt is the minimum RTT of the entire transmission.
            NS_ASSERT(m_minRtt >= m_baseRtt);
            Time rttQueue = m_minRtt - m_baseRtt;

            // queue = rttQueue * bw = rttQueue * (cwnd/RTTmin)
            double bw = segCwnd / m_minRtt.GetSeconds();
            auto queue = static_cast<uint32_t>(bw * rttQueue.GetSeconds());
            NS_LOG_DEBUG("Queue backlog = " << queue << " given by cwnd = " << segCwnd
                                            << ", minRtt = " << m_minRtt.GetMilliSeconds()
                                            << " ms, baseRtt = " << m_baseRtt.GetMilliSeconds()
                                            << " ms");

            double L = rttQueue.GetSeconds() / m_baseRtt.GetSeconds();
            NS_LOG_DEBUG("Network congestion level = " << L);

            if (queue > m_alpha || L > (1 / m_phy))
            { // Slow mode
                if (queue > m_alpha && segCwnd > m_renoCount)
                { // Precautionary decongestion
                    NS_LOG_LOGIC("Execute the precautionary decongestion.");
                    uint32_t reduction = std::min(queue / m_gamma, segCwnd >> m_epsilon);

                    // Modification
                    reduction = static_cast<uint32_t>(reduction * decayFactor);
                    // Modification

                    segCwnd -= reduction;
                    segCwnd = std::max(segCwnd, m_renoCount);
                    tcb->m_cWnd = segCwnd * tcb->m_segmentSize;
                    tcb->m_ssThresh = (tcb->m_cWnd);

                    // Modification
                    // tcb->m_ssThresh = (tcb->m_cWnd)*(2.0);
                    // Modificationo

                    NS_LOG_INFO("In Slow mode, after precautionary decongestion, "
                                "updated to cwnd "
                                << tcb->m_cWnd << " ssthresh " << tcb->m_ssThresh);
                }

                if (m_renoCount <= 2)
                {
                    m_renoCount = std::max(segCwnd >> 1, static_cast<uint32_t>(2));
                }
                else
                {
                    m_renoCount++;
                }

                m_doingRenoNow = m_doingRenoNow + 1;
                NS_LOG_DEBUG("In Slow mode, updated to m_renoCount = "
                             << m_renoCount << " m_doingRenoNow = " << m_doingRenoNow);
            }
            else
            { // Fast mode
                m_fastCount++;
                if (m_fastCount > m_zeta)
                { // Reset renoCount
                    m_renoCount = 2;
                    m_fastCount = 0;
                }
                m_doingRenoNow = 0;
                NS_LOG_DEBUG("In Fast mode, updated to m_renoCount = "
                             << m_renoCount << " m_doingRenoNow = " << m_doingRenoNow);
            }
            m_lastQ = queue;
        }

        // Save the current right edge for next Yeah cycle
        m_begSndNxt = tcb->m_nextTxSequence;

        // Reset cntRtt & minRtt
        m_cntRtt = 0;
        m_minRtt = Time::Max();
    }
}

std::string
TcpYeahMy::GetName() const
{
    return "TcpYeahMy";
}

uint32_t
TcpYeahMy::GetSsThresh(Ptr<const TcpSocketState> tcb, uint32_t bytesInFlight)
{
    NS_LOG_FUNCTION(this << tcb << bytesInFlight);
    uint32_t reduction;
    uint32_t segBytesInFlight = bytesInFlight / tcb->m_segmentSize;

    if (m_doingRenoNow < m_rho)
    { // Not competing with Reno flows
        NS_LOG_LOGIC("Not competing with Reno flows upon loss");
        reduction = m_lastQ;
        reduction = std::max(reduction, segBytesInFlight >> m_delta);
        reduction = std::min(reduction, std::max(segBytesInFlight >> 1, 2U));
    }
    else
    { // Competing with Reno flows
        NS_LOG_LOGIC("Competing with Reno flows upon loss");
        reduction = std::max(segBytesInFlight >> 1, static_cast<uint32_t>(2));

        // Modification
        // uint32_t temp = segBytesInFlight*0.8;
        // reduction = std::max(temp, static_cast<uint32_t>(2));
        // Modification

    }

    NS_LOG_INFO("Reduction amount upon loss = " << reduction);

    m_fastCount = 0;
    m_renoCount = std::max(m_renoCount >> 1, static_cast<uint32_t>(2));

    // Allow, at least, 2 segment to go out
    uint32_t ret =
        std::max(bytesInFlight - (reduction * tcb->m_segmentSize), 2U * tcb->m_segmentSize);
    return ret;
}

} // namespace ns3
