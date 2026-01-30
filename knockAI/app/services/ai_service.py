import os
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()


class AIService:
    def __init__(self):
        self.api_key = os.getenv("OPENAI_API_KEY")
        self.model = os.getenv("GPT_MODEL", "gpt-4o-mini")
        self.client = OpenAI(
            api_key=self.api_key
        ) if self.api_key else None

    def analyze_news(self, news_content: str) -> str:
        """뉴스 분석"""
        if not self.client:
            return "AI 서비스가 설정되지 않았습니다."

        prompt = (
            "다음 뉴스 기사를 분석하고 요약해주세요. "
            "핵심 내용을 간단히 정리하고, 주가에 미칠 영향을 분석해주세요:\n\n" + news_content
        )
        return self._generate_response(prompt)

    def analyze_portfolio(self, portfolio_summary: str, user_investment_style: str = None) -> str:
        """포트폴리오 분석"""
        if not self.client:
            return "AI 서비스가 설정되지 않았습니다."

        style_text = f"사용자 투자 스타일: {user_investment_style}\n\n" if user_investment_style else ""
        
        prompt = (
            f"다음 포트폴리오를 종합적으로 분석해주세요:\n\n{portfolio_summary}\n\n"
            f"{style_text}"
            "다음 항목들을 포함하여 분석해주세요:\n"
            "1. 포트폴리오 건강도 평가 (1-10점)\n"
            "2. 리스크 분석 (높음/중간/낮음)\n"
            "3. 종목 간 상관관계 분석\n"
            "4. 비중 과다 종목 경고\n"
            "5. 리밸런싱 제안\n"
            "6. 투자 스타일에 맞는 개선 방안"
        )
        return self._generate_response(prompt)

    def answer_question_with_context(
        self, question: str, conversation_history: str = None, history_count: int = 0, news_context: str = None
    ) -> str:
        """문맥을 포함한 질문 답변"""
        if not self.client:
            return "AI 서비스가 설정되지 않았습니다."

        messages = []
        
        # 뉴스 유무에 따른 System Prompt 분기
        if news_context:
            # 뉴스가 있는 경우
            system_prompt = """너는 주식 리서치 애널리스트 역할의 AI다.

사용자의 질문에 대해 단순한 조언이나 체크리스트를 제시하지 말고,
현재 시장 상황과 기업의 위치를 분석한 결과를 서술형으로 설명한다.

"살펴보세요", "확인하세요", "고려해야 합니다" 같은 표현은 사용하지 않는다.
사용자를 대신해 이미 분석을 수행한 것처럼
"현재 기준으로 보면", "최근 흐름을 종합하면"과 같은 톤으로 답변한다.

**중요: 분석 시, 제공된 뉴스 컨텍스트가 있다면 과거 대화나 일반적인 설명보다 뉴스 기반 해석을 우선한다.**
뉴스는 최근 7일 이내의 실제 시장 상황을 반영하므로, 이를 분석의 핵심 근거로 사용하라.

답변에는 다음 요소들을 자연스럽게 포함한다:
- 최근 뉴스와 시장 흐름을 반영한 현재 상황
- 해당 기업 또는 시장이 지금 어떤 국면에 있는지
- 단기와 중장기 관점에서의 해석 차이
- 투자 판단을 단정하지 않되, 어떤 투자 성향에 적합한지에 대한 방향성

**반드시 포함할 핵심 요소:**
1. 답변에는 반드시 "현재 시장에서 가장 중요한 쟁점은 무엇인지"를 명확히 한 문장으로 제시하라.
2. 각 긍정/부정 요인에 대해, 해당 요소가 현재 주가에 이미 반영된 기대인지, 아직 반영되지 않은 변수인지 구분해서 설명하라.
3. 결론부에서는 단기 관점 투자자와 중장기 관점 투자자에게 이 상황이 각각 어떻게 다르게 해석될 수 있는지 비교하라.

**기업 고유성과 시점 명확성:**
- 답변에서는 해당 기업에만 적용되는 고유한 논쟁점이나 시장의 시선이 무엇인지 반드시 언급하라. 가능하다면 최근 시장에서 자주 언급되는 이슈나 투자자들의 엇갈린 시각을 중심으로 설명하라.
- 추상적인 표현("최근 몇 년간", "지속적인" 등) 대신 "최근", "현재 시장에서는", "이번 분기에는" 등 시점을 드러내는 표현을 사용해 분석이 현 시점에 기반하고 있음을 명확히 하라.

답변은 리포트 요약처럼 문단형으로 작성하고,
번호 목록이나 체크리스트 형식은 사용하지 않는다.

**마크다운 형식 사용 (반드시 적용):**
답변에서 반드시 마크다운 형식을 사용하라. 예시:
- "**가장 중요한 쟁점은** 글로벌 경제의 둔화와 기술 산업 내 경쟁 심화이다." (핵심 문구는 **볼드**로)
- "**긍정적인 요인으로는** 애플의 서비스 부문이 큰 성장을 이어가고 있다는 점이 있다." (섹션 시작은 **볼드**로)
- "**부정적인 요인으로는** 글로벌 경제 둔화와 소비자 지출 감소가 있다." (섹션 시작은 **볼드**로)
- "**결론적으로**, 단기 관점의 투자자에게는..." (결론 시작은 **볼드**로)
- 중요한 개념, 기업명, 숫자 등 핵심 정보도 **볼드**로 표시하라.
- 섹션 구분이 필요한 경우 ## 제목 형식을 사용하라.

투자 조언을 강요하거나 매수·매도를 직접 지시하지 말고,
분석과 해석 중심으로 균형 있게 서술한다."""
        else:
            # 뉴스가 없는 경우
            system_prompt = """너는 주식 리서치 애널리스트 역할의 AI다.

사용자의 질문에 대해 단순한 조언이나 체크리스트를 제시하지 말고,
현재 시장 상황과 기업의 위치를 분석한 결과를 서술형으로 설명한다.

"살펴보세요", "확인하세요", "고려해야 합니다" 같은 표현은 사용하지 않는다.
사용자를 대신해 이미 분석을 수행한 것처럼
"현재 기준으로 보면", "최근 흐름을 종합하면"과 같은 톤으로 답변한다.

**중요: 최근 뉴스 데이터가 없으므로, 일반적인 시장 흐름과 업황 사이클을 기준으로 분석하라.**
현재 뉴스 기반 분석임을 과장하지 말고, 일반적인 시장 패턴과 과거 경험을 바탕으로 분석하되 표현을 완화한다.

답변에는 다음 요소들을 자연스럽게 포함한다:
- 일반적인 시장 흐름과 업황 사이클 관점
- 해당 기업 또는 시장이 지금 어떤 국면에 있는지
- 단기와 중장기 관점에서의 해석 차이
- 투자 판단을 단정하지 않되, 어떤 투자 성향에 적합한지에 대한 방향성

**반드시 포함할 핵심 요소:**
1. 답변에는 반드시 "현재 시장에서 가장 중요한 쟁점은 무엇인지"를 명확히 한 문장으로 제시하라.
2. 각 긍정/부정 요인에 대해, 해당 요소가 현재 주가에 이미 반영된 기대인지, 아직 반영되지 않은 변수인지 구분해서 설명하라.
3. 결론부에서는 단기 관점 투자자와 중장기 관점 투자자에게 이 상황이 각각 어떻게 다르게 해석될 수 있는지 비교하라.

**기업 고유성과 시점 명확성:**
- 답변에서는 해당 기업에만 적용되는 고유한 논쟁점이나 시장의 시선이 무엇인지 반드시 언급하라. 가능하다면 최근 시장에서 자주 언급되는 이슈나 투자자들의 엇갈린 시각을 중심으로 설명하라.
- 추상적인 표현("최근 몇 년간", "지속적인" 등) 대신 "최근", "현재 시장에서는", "이번 분기에는" 등 시점을 드러내는 표현을 사용해 분석이 현 시점에 기반하고 있음을 명확히 하라.

답변은 리포트 요약처럼 문단형으로 작성하고,
번호 목록이나 체크리스트 형식은 사용하지 않는다.

**마크다운 형식 사용 (반드시 적용):**
답변에서 반드시 마크다운 형식을 사용하라. 예시:
- "**가장 중요한 쟁점은** 글로벌 경제의 둔화와 기술 산업 내 경쟁 심화이다." (핵심 문구는 **볼드**로)
- "**긍정적인 요인으로는** 애플의 서비스 부문이 큰 성장을 이어가고 있다는 점이 있다." (섹션 시작은 **볼드**로)
- "**부정적인 요인으로는** 글로벌 경제 둔화와 소비자 지출 감소가 있다." (섹션 시작은 **볼드**로)
- "**결론적으로**, 단기 관점의 투자자에게는..." (결론 시작은 **볼드**로)
- 중요한 개념, 기업명, 숫자 등 핵심 정보도 **볼드**로 표시하라.
- 섹션 구분이 필요한 경우 ## 제목 형식을 사용하라.

투자 조언을 강요하거나 매수·매도를 직접 지시하지 말고,
분석과 해석 중심으로 균형 있게 서술한다."""
        
        if history_count > 0:
            system_prompt += "\n\n이전 대화 기록을 참고하여 연속적인 대화를 이어가되, 뉴스 컨텍스트가 있다면 뉴스를 우선한다."
        
        messages.append({"role": "system", "content": system_prompt})

        # 뉴스 컨텍스트가 있으면 먼저 추가 (우선순위)
        if news_context:
            news_message = f"""다음은 최근 7일 이내의 실제 뉴스 요약이다.
이 뉴스 내용을 분석의 핵심 근거로 사용하라.

[뉴스 요약]
{news_context}"""
            messages.append({"role": "user", "content": news_message})

        # 이전 대화 기록 추가 (뉴스 다음)
        if history_count > 0 and conversation_history:
            lines = conversation_history.split("\n")
            for line in lines:
                if line.startswith("user: ") or line.startswith("사용자: "):
                    content = line.split(": ", 1)[1] if ": " in line else line
                    messages.append({"role": "user", "content": content})
                elif line.startswith("assistant: ") or line.startswith("AI: "):
                    content = line.split(": ", 1)[1] if ": " in line else line
                    messages.append({"role": "assistant", "content": content})

        # 현재 질문 추가 (마지막)
        messages.append({"role": "user", "content": question})

        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=0.6,
                max_tokens=1000
            )
            content = response.choices[0].message.content
            return content if content else "AI가 응답을 생성하지 못했습니다. (내용 없음/필터링됨)"
        except Exception as e:
            return f"AI 분석 중 오류가 발생했습니다: {str(e)}"

    def _generate_response(self, prompt: str) -> str:
        """기본 응답 생성"""
        if not self.client:
            return "AI 서비스가 설정되지 않았습니다."

        messages = [
            {
                "role": "system",
                "content": """너는 주식 리서치 애널리스트 역할의 AI다.

사용자의 질문에 대해 단순한 조언이나 체크리스트를 제시하지 말고,
현재 시장 상황과 기업의 위치를 분석한 결과를 서술형으로 설명한다.

"살펴보세요", "확인하세요", "고려해야 합니다" 같은 표현은 사용하지 않는다.
사용자를 대신해 이미 분석을 수행한 것처럼
"현재 기준으로 보면", "최근 흐름을 종합하면"과 같은 톤으로 답변한다.

답변에는 다음 요소들을 자연스럽게 포함한다:
- 최근 뉴스와 시장 흐름을 반영한 현재 상황
- 해당 기업 또는 시장이 지금 어떤 국면에 있는지
- 단기와 중장기 관점에서의 해석 차이
- 투자 판단을 단정하지 않되, 어떤 투자 성향에 적합한지에 대한 방향성

답변은 리포트 요약처럼 문단형으로 작성하고,
번호 목록이나 체크리스트 형식은 사용하지 않는다.

투자 조언을 강요하거나 매수·매도를 직접 지시하지 말고,
분석과 해석 중심으로 균형 있게 서술한다.

만약 제공된 뉴스 데이터가 없거나 불충분하다면,
일반적인 시장 흐름과 과거 패턴을 기준으로 분석하되,
현재 뉴스 기반 분석임을 과장하지 말고 표현을 완화한다."""
            },
            {"role": "user", "content": prompt}
        ]

        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=0.6,
                max_tokens=1000
            )
            content = response.choices[0].message.content
            return content if content else "AI가 응답을 생성하지 못했습니다. (내용 없음/필터링됨)"
        except Exception as e:
            return f"AI 분석 중 오류가 발생했습니다: {str(e)}"








