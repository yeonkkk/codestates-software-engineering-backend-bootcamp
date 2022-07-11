package com.codestates.member.service;

import com.codestates.event.SendMailEvent;
import com.codestates.exception.BusinessLogicException;
import com.codestates.exception.ExceptionCode;
import com.codestates.member.entity.Member;
import com.codestates.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 *  - 메서드 구현
 *  - DI 적용
 *  - Spring Data JPA 적용
 *  - 트랜잭션 적용
 */
@Slf4j
@Transactional
@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher publisher;

    public MemberService(MemberRepository memberRepository, ApplicationEventPublisher publisher) {
        this.memberRepository = memberRepository;
        this.publisher = publisher;
    }

    public Member createMember(Member member) {
        verifyExistsEmail(member.getEmail());
        Member savedMember = memberRepository.save(member);
        /**
         * TODO
         *  - 현재 이메일 전송 중 5초 뒤에 예외가 발생합니다.
         *  - 이메일 전송에 실패할 경우에만, 앞에서 DB에 저장된 회원 정보를 삭제(rollback)하도록
         *  코드를 구현하세요.
         *
         *  *****     추가 설명     ********
         *  - 이메일이 비동기적으로 전송되기 때문에 MemberService에서 이메일을 전송하면 이메일 전송에 실패해도 회원 정보가 rollback이 되지 않습니다.
         *  - 따라서 이메일 전송을 MemberService에서 하는 것은 의미가 없을 가능성이 높습니다.
         *  - Spring에서는 Event를 Publish(발행)하는 기능이 있으며, 회원 등록 자체를 이벤트로 보고 회원이 등록되었다는 이벤트를 애플리케이션 전체에 보낼 수 있습니다.
         *      - MemberService에서 회원 등록 이벤트를 비동기 적으로 먼저 보내고 이 이벤트를 리스닝(Listening)하는 곳에서 이메일을 보낼 수 있습니다.
         *      - 이벤트 리스너(Event Listener)가 이메일을 보내고 실패할 경우 이미 저장된 회원 정보를 삭제할 수 있습니다.
         */
        publisher.publishEvent(new SendMailEvent(this, savedMember));
        return savedMember;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public Member updateMember(Member member) {
        Member findMember = findVerifiedMember(member.getMemberId());

        Optional.ofNullable(member.getName())
                .ifPresent(name -> findMember.setName(name));
        Optional.ofNullable(member.getPhone())
                .ifPresent(phone -> findMember.setPhone(phone));
        Optional.ofNullable(member.getMemberStatus())
                .ifPresent(memberStatus -> findMember.setMemberStatus(memberStatus));

        return memberRepository.save(findMember);
    }

    @Transactional(readOnly = true)
    public Member findMember(long memberId) {
        return findVerifiedMember(memberId);
    }

    public Page<Member> findMembers(int page, int size) {
        return memberRepository.findAll(PageRequest.of(page, size,
                Sort.by("memberId").descending()));
    }

    public void deleteMember(long memberId) {
        Member findMember = findVerifiedMember(memberId);

        memberRepository.delete(findMember);
    }

    @Transactional(readOnly = true)
    public Member findVerifiedMember(long memberId) {
        Optional<Member> optionalMember =
                memberRepository.findById(memberId);
        Member findMember =
                optionalMember.orElseThrow(() ->
                        new BusinessLogicException(ExceptionCode.MEMBER_NOT_FOUND));
        return findMember;
    }

    private void verifyExistsEmail(String email) {
        Optional<Member> member = memberRepository.findByEmail(email);
        if (member.isPresent())
            throw new BusinessLogicException(ExceptionCode.MEMBER_EXISTS);
    }
}
